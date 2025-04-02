import com.huaban.analysis.jieba.JiebaSegmenter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 倒排索引构建类
 */
class InvertedIndexBuilder {
    private final JiebaSegmenter segmenter = new JiebaSegmenter();
    private final Set<String> allFiles = new HashSet<>(); // 存储所有文件名字

    // 构建倒排索引，用HashMap存储，文件名集合也用HashSet存储
    public Map<String, Set<String>> buildIndex(String directoryPath) {
        Map<String, Set<String>> invertedIndex = new HashMap<>();
        // 遍历目录，处理每个文件
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> processFile(path, invertedIndex));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return invertedIndex;
    }

    // 处理单个文件，把关键词及其所在文件名记录到倒排索引中
    private void processFile(Path path, Map<String, Set<String>> invertedIndex) {
        try {
            String fileName = path.getFileName().toString(); // 仅获取文件名
            allFiles.add(fileName); // 记录所有文件名

            String content = new String(Files.readAllBytes(path));
            List<String> words = segmenter.sentenceProcess(content);
            for (String word : words) {
                // 过滤无效词
                if (isValidWord(word)) {
                    // 记录关键词及其所在文件名
                    invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(fileName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 过滤无效词
    private boolean isValidWord(String word) {
        return word.trim().length() > 1 && !word.matches("[\\d\\p{Punct}\\s]+"); // 过滤单字、标点、数字、空白字符
    }

    // 获得所有文件名，用于后续取差集
    public Set<String> getAllFileNames() {
        return allFiles;
    }
}


/**
 * 中缀表达式转后缀表达式的转换器
 * 输入：中缀表达式，字符串形式
 * 输出：后缀表达式，字符串列表
 */
class InfixToPostfixConverter {
    // 存储运算符优先级，代码更简洁
    private static final Map<String, Integer> PRECEDENCE = new HashMap<>();
    /*
     * static 语句块：
     * 只执行一次:无论创建多少个对象，静态语句块只会执行一次。
     * 与类关联:静态语句块是在类级别上定义的，而不是在对象级别。
     * 运行时机:在JVM加载类时执行，比任何构造函数或实例代码先执行
     */
    static {
        PRECEDENCE.put("NOT", 3);  // NOT 最高优先级
        PRECEDENCE.put("AND", 2);  // AND 次高优先级
        PRECEDENCE.put("OR", 1);   // OR 最低优先级
    }

    /**
     * 将中缀表达式字符串转换为列表，便于后续处理
     * @param query 中缀表达式字符串
     * @return 中缀表达式字符串的列表
     */
    public static List<String> queryToList(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        for (char ch : query.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else if (ch == '(' || ch == ')') {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
                tokens.add(String.valueOf(ch));
            } else {
                currentToken.append(ch);
            }
        }
        // 勿忘最后一个词
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens;
    }

    /**
     * 将中缀表达式转换为后缀表达式
     * @param query 中缀表达式（原始字符串形式）
     * @return 后缀表达式（列表形式）
     */
    public static List<String> toPostfix(String query) {
        List<String> tokens = queryToList(query);
        List<String> output = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();

        for (String token : tokens) {
            if (token.equals("(")) {
                stack.push(token);
            } else if (token.equals(")")) {
                // 弹出并输出栈中所有运算符直到遇到左括号 '('
                while (!stack.isEmpty() && !stack.peek().equals("(")) {
                    output.add(stack.pop());
                }
                stack.pop();  // 弹出左括号 '(' 左括号不加入输出列表
            } else if (PRECEDENCE.containsKey(token.toUpperCase())) {  // 逻辑运算符
                while (!stack.isEmpty() &&
                        PRECEDENCE.getOrDefault(stack.peek(), 0) >= PRECEDENCE.get(token)) {
                    output.add(stack.pop());
                }
                stack.push(token);
            } else {
                output.add(token); // 关键词
            }
        }
        // 弹出剩余运算符
        while (!stack.isEmpty()) {
            output.add(stack.pop());
        }
        return output;
    }
}

/**
 * 后缀表达式求值器
 */
class PostfixEvaluator {
    /**
     * 计算后缀表达式的结果
     * @param postfixTokens 后缀表达式列表形式
     * @param invertedIndex 倒排索引
     * @param allFiles      所有文件名的集合
     * @return 匹配的文件名集合，hashset形式
     */
    public static Set<String> evaluatePostfix(List<String> postfixTokens, Map<String, Set<String>> invertedIndex, Set<String> allFiles) {
        // 栈用于存储某个检索词的索引集
        Deque<Set<String>> stack = new ArrayDeque<>();
        for (String token : postfixTokens) {
            // token 是运算符
            if (token.equalsIgnoreCase("AND") || token.equalsIgnoreCase("OR") || token.equalsIgnoreCase("NOT")) {
                Set<String> result;
                if (token.equalsIgnoreCase("NOT")) {
                    result = difference(stack.pop(), allFiles);
                } else {
                    // 对栈顶的两元素取交集/并集，把结果重新压入栈
                    Set<String> set1 = stack.pop();
                    Set<String> set2 = stack.pop();
                    result = token.equalsIgnoreCase("AND") ? intersection(set1, set2) : union(set1, set2);
                }
                stack.push(result);
            }
            // token是关键词
            else {
                // 关键词，取倒排索引
                stack.push(invertedIndex.getOrDefault(token, new HashSet<>()));
            }
        }
        return stack.isEmpty() ? Collections.emptySet() : stack.pop();
    }

    private static Set<String> intersection(Set<String> set1, Set<String> set2) {
        Set<String> result = new HashSet<>(set1);  // 创建 set1 的副本
        result.retainAll(set2);                    // 保留 set1 中在 set2 中也存在的元素
        return result;
    }

    private static Set<String> union(Set<String> set1, Set<String> set2) {
        Set<String> result = new HashSet<>(set1);
        result.addAll(set2);
        return result;
    }

    private static Set<String> difference(Set<String> All, Set<String> set1) {
        Set<String> result = new HashSet<>(set1);
        result.removeAll(All);
        return result;
    }
}

public class Main {
    public static void main(String[] args) {
        String directoryPath = "dataset/article";
        InvertedIndexBuilder indexBuilder = new InvertedIndexBuilder();
        Map<String, Set<String>> invertedIndex = indexBuilder.buildIndex(directoryPath);
        Set<String> allFiles = indexBuilder.getAllFileNames();  // 所有文件名的集合

        Scanner sc = new Scanner(System.in);
        System.out.println("请输入查询语句：");

        while (true) {
            System.out.print("> ");
            String query = sc.nextLine().trim();   // 去除前后空格
            if (query.equalsIgnoreCase("exit")) break;

            long startTime = System.nanoTime();

            List<String> postfixTokens = InfixToPostfixConverter.toPostfix(query);
            Set<String> resultFiles = PostfixEvaluator.evaluatePostfix(postfixTokens, invertedIndex, allFiles);

            long endTime = System.nanoTime();

            System.out.println("查询用时：" + (endTime - startTime) / 1_000_000.0 + " ms");
            System.out.println("查询结果：" + (resultFiles.isEmpty() ? "无匹配文件" : resultFiles));
        }

        sc.close();
    }
}

