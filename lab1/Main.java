import java.io.*;
import java.util.*;

class TrieNode{
    HashMap<Character, TrieNode> children;  // 用HashMap来实现Trie树，用字符作为键mapping到下一层的节点
    boolean isEnd;  // 是否可作为词尾

    public TrieNode() {
        children = new HashMap<>();
        isEnd = false;
    }
}

class Trie {
    TrieNode root;
    public Trie() {
        root = new TrieNode();
    }
    /**
     * 向Trie树插入一个词
     * @param word 待插入的词
     */
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (!node.children.containsKey(c)) {
                node.children.put(c, new TrieNode());
            }
            node = node.children.get(c);
        }
        node.isEnd = true;
    }

    /**
     * 在Trie树中查找一个词，如果查不到，返回的是最长前缀的词，可能为null
     * @param str 长度为maxLen（或小于maxLen）的待分词字符串
     * @return 最长前缀的词，当str中没有树中的词时返回null
     */
    public String searchLongestPrefix(String str) {
        TrieNode node = root;
        String res = null;
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (node.children.containsKey(c)) {
                sb.append(c);
                node = node.children.get(c);
                if (node.isEnd) {
                    res = sb.toString();
                }
            } else {
                break;
            }
        }
        return res;
    }

}

public class Main {
    /**
     * 前向最大匹配算法（Trie树优化版）
     * @param str 待分词的字符串
     * @param dict 词典Trie树对象
     * @param maxLen 最大匹配长度
     * @return 分词后的字符串的列表
     */
    public static List<String> fmm(String str, Trie dict, int maxLen) {
        // 初始条件，待处理字符串为整个字符串
        String remainder = str;
        List<String> res = new ArrayList<>();
        while (remainder.length() > 0) {
            // 取前maxLen个字符作为匹配词
            String cur = remainder.substring(0, Math.min(maxLen, remainder.length()));
            // 在Trie树中查找最长前缀的词
            String longestPrefix = dict.searchLongestPrefix(cur);
            if (longestPrefix!= null) {
                // 找到匹配词，将其加入结果字符串
                res.add(longestPrefix);
                // 截取待处理字符串，去掉匹配词，设置flag
                remainder = remainder.substring(longestPrefix.length());
            } else {
                // 没有找到，就取第一个字
                res.add(cur.substring(0, 1));
                remainder = remainder.substring(1);
            }
        }
        return res;
    }

    public static void main(String[] args) {
        // 读取corpus.dict.txt文件 - 所有词汇读入dict列表
        Trie dict = new Trie();
        // 在圆括号里写初始化可以不用手动br.close()
        try (BufferedReader br = new BufferedReader(new FileReader("corpus.dict.txt"))){
            String line;
            while ((line = br.readLine()) != null)
                dict.insert(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 读取corpus.sentence.txt文件 - 所有句子读入sentences列表
        List<String> sentences = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("corpus.sentence.txt"))){
            String line;
            while ((line = br.readLine()) != null)
                sentences.add(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 读取cn_stopwords.txt - 所有停止词读入stopwords数组
        List<String> stopwords = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("cn_stopwords.txt"))){
            String line;
            while ((line = br.readLine()) != null)
                stopwords.add(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 调用函数
        // 词频映射
        Map<String, Integer> wordCount = new HashMap<>();
        int totalWords = 0; // 统计总词数
        // 处理句子并写入结果
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt", false))){
            // 打印分词结果到result.txt文件
            for (String sentence : sentences) {
                List<String> words = fmm(sentence, dict, 7);
                for (String word : words) {
                    bw.write(word + "/");
                    // 过滤停用词，统计词频
                    if (!stopwords.contains(word)) {
                        wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
                        totalWords++;
                    }
                }
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 输出前词频10
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(wordCount.entrySet());
        sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("result_freq.json", false))){
            // 词频统计，写成字典格式到result_freq.json文件（除以总词数，得到词频）
            bw.write("{\n");
            for (int i = 0; i < 10; i++) {
                Map.Entry<String, Integer> entry = sortedEntries.get(i);
                bw.write("\t\"" + entry.getKey() + "\": " + (double)entry.getValue() / totalWords + (i == 9 ? "" : ",\n"));
            }
            bw.write("\n}");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
