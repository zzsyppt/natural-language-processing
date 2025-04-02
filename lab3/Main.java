import com.huaban.analysis.jieba.JiebaSegmenter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BooleanQueryParser {
    //private static final Set<String> OPERATORS = new HashSet<>(Arrays.asList("AND", "OR", "NOT"));

    public static List<String> tokenize(String query) {
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

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}
class ShuntingYard {
    private static final Map<String, Integer> PRECEDENCE = new HashMap<>();

    static {
        PRECEDENCE.put("NOT", 3);  // NOT 最高优先级
        PRECEDENCE.put("AND", 2);  // AND 次高优先级
        PRECEDENCE.put("OR", 1);   // OR 最低优先级
    }

    public static List<String> toPostfix(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Deque<String> operatorStack = new ArrayDeque<>();

        for (String token : tokens) {
            if (token.equals("(")) {
                operatorStack.push(token);
            } else if (token.equals(")")) {
                while (!operatorStack.isEmpty() && !operatorStack.peek().equals("(")) {
                    output.add(operatorStack.pop());
                }
                operatorStack.pop();  // 弹出左括号 '('
            } else if (PRECEDENCE.containsKey(token.toUpperCase())) {  // 逻辑运算符
                while (!operatorStack.isEmpty() &&
                        PRECEDENCE.getOrDefault(operatorStack.peek(), 0) >= PRECEDENCE.get(token)) {
                    output.add(operatorStack.pop());
                }
                operatorStack.push(token);
            } else {
                output.add(token); // 关键词
            }
        }

        while (!operatorStack.isEmpty()) {
            output.add(operatorStack.pop());
        }

        return output;
    }
}
class PostfixEvaluator {
    public static Set<String> evaluatePostfix(List<String> postfixTokens, Map<String, Set<String>> invertedIndex, Set<String> allFiles) {
        Deque<Set<String>> stack = new ArrayDeque<>();

        for (String token : postfixTokens) {
            if (token.equalsIgnoreCase("AND") || token.equalsIgnoreCase("OR") || token.equalsIgnoreCase("NOT")) {
                Set<String> result;
                if (token.equalsIgnoreCase("NOT")) {
                    result = difference(stack.pop(), allFiles);
                } else {
                    Set<String> right = stack.pop();
                    Set<String> left = stack.pop();
                    result = token.equalsIgnoreCase("AND") ? intersection(left, right) : union(left, right);
                }
                stack.push(result);
            } else {
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

    private static Set<String> difference(Set<String> set1, Set<String> set2) {
        Set<String> result = new HashSet<>(set2);
        result.removeAll(set1);
        return result;
    }
}

public class Main {
    public static void main(String[] args) {
        String directoryPath = "dataset/article";
        InvertedIndexBuilder indexBuilder = new InvertedIndexBuilder();
        Map<String, Set<String>> invertedIndex = indexBuilder.buildIndex(directoryPath);
        Set<String> allFiles = indexBuilder.getAllFiles();

        Scanner scanner = new Scanner(System.in);
        System.out.println("请输入查询语句：");

        while (true) {
            System.out.print("> ");
            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("exit")) break;

            long startTime = System.nanoTime();

            List<String> tokens = BooleanQueryParser.tokenize(query);
            List<String> postfixTokens = ShuntingYard.toPostfix(tokens);
            Set<String> resultFiles = PostfixEvaluator.evaluatePostfix(postfixTokens, invertedIndex, allFiles);

            long endTime = System.nanoTime();
            System.out.println("查询用时：" + (endTime - startTime) / 1_000_000.0 + " ms");
            System.out.println("查询结果：" + (resultFiles.isEmpty() ? "无匹配文件" : resultFiles));
        }

        scanner.close();
    }
}


/**
 * 倒排索引构建类
 */
class InvertedIndexBuilder {
    private final JiebaSegmenter segmenter = new JiebaSegmenter();
    private final Set<String> allFiles = new HashSet<>(); // 存储所有文件路径

    public Map<String, Set<String>> buildIndex(String directoryPath) {
        Map<String, Set<String>> invertedIndex = new HashMap<>();
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> processFile(path, invertedIndex));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return invertedIndex;
    }

    private void processFile(Path path, Map<String, Set<String>> invertedIndex) {
        try {
            String fileName = path.getFileName().toString(); // 仅获取文件名
            allFiles.add(fileName); // 记录所有文件名

            String content = new String(Files.readAllBytes(path));
            List<String> words = segmenter.sentenceProcess(content);
            for (String word : words) {
                if (isValidWord(word)) {
                    invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(fileName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isValidWord(String word) {
        return word.trim().length() > 1 && !word.matches("[\\d\\p{Punct}\\s]+"); // 过滤单字、标点、数字、空白字符
    }

    public Set<String> getAllFiles() {
        return allFiles;
    }
}


