import java.io.*;
import java.util.*;

public class TimeTest {
    /**
     * 前向最大匹配算法
     * @param str 待分词的字符串
     * @param dict 词典列表对象
     * @param maxLen 最大匹配长度
     * @return 分词后的字符串的列表
     */
    public static List<String> fmm(String str, List<String> dict, int maxLen) {
        // 初始条件，待处理字符串为整个字符串
        String remainder = str;
        List<String> res = new ArrayList<>();
        while (remainder.length() > 0) {
            // 取前maxLen个字符作为匹配词
            String cur = remainder.substring(0, Math.min(maxLen, remainder.length()));
            while(cur.length() > 0) {
                // 看看有没有匹配上的词 （可以用for循环，也可以用包装好的contains方法，时间差别不大）
                if (dict.contains(cur)){
                    // 找到匹配词，将其加入res
                    res.add(cur);
                    // 截取待处理字符串，去掉匹配词
                    remainder = remainder.substring(cur.length());
                    break;
                }
                // 没有找到，就缩减一个字。注意临界条件
                if (cur.length() == 1) {
                    res.add(cur);
                    remainder = remainder.substring(1);
                    break;
                }
                cur = cur.substring(0, cur.length() - 1);
            }
        }
        return res;
    }

    /**
     * 前向最大匹配算法 Trie树优化版
     * @param str 待分词的字符串
     * @param dict 词典前缀树对象
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
                // 找到匹配词，将其加入res
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

    /**
     * 前向最大匹配算法 优化版 按字数分割词典
     * @param str 待分词的字符串
     * @param dictMap 从整数到词典列表的映射，按字典中的单词长度映射到相应字典
     * @param maxLen 最大匹配长度
     * @return 分词后的字符串的列表
     */
    public static List<String> fmm(String str, HashMap<Integer, List<String>> dictMap, int maxLen) {
        // 初始条件，待处理字符串为整个字符串
        String remainder = str;
        List<String> res = new ArrayList<>();
        while (remainder.length() > 0) {
            // 取前maxLen个字符作为匹配词
            String cur = remainder.substring(0, Math.min(maxLen, remainder.length()));
            while(cur.length() > 0) {
                // 看看有没有匹配上的词。先找到对应长度的字典。
                List<String> dict = dictMap.get(cur.length());
                // 没有对应长度的字典，就缩减一个字
                if (dict == null){
                    // 注意临界条件
                    if (cur.length() == 1) {
                        res.add(cur);
                        remainder = remainder.substring(1);
                        break;
                    }
                    cur = cur.substring(0, cur.length() - 1);
                    continue;
                }
                if (dict.contains(cur)){
                    // 找到匹配词，将其加入res
                    res.add(cur);
                    // 截取待处理字符串，去掉匹配词
                    remainder = remainder.substring(cur.length());
                    break;
                }
                // 没有找到，就缩减一个字。注意临界条件
                if (cur.length() == 1) {
                    res.add(cur);
                    remainder = remainder.substring(1);
                    break;
                }
                cur = cur.substring(0, cur.length() - 1);
            }
        }
        return res;
    }

    public static void main(String[] args) {
        version1();
        version2();
        version3();
    }

    public static void version1(){
        // 读取corpus.dict.txt文件 - 所有词汇读入dict列表
        List<String> dict = new ArrayList<>();
        // （语法知识）在圆括号里写初始化可以不用手动br.close()
        try (BufferedReader br = new BufferedReader(new FileReader("corpus.dict.txt"))){
            String line;
            while ((line = br.readLine()) != null)
                dict.add(line);
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
        // 开始计时（用最精确的计时方法）
        long start = System.nanoTime();
        for (String sentence : sentences) {
            List<String> words = fmm(sentence, dict, 7);
        }
        // 结束计时
        long end = System.nanoTime();
        System.out.println("Version 1 (ArrayList): Time used: " + (end - start) / 1000000000.0 + "s");

        // 将结果写入文件-用于验证
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("timetest_v1.txt", false))){
            // 打印分词结果到result.txt文件
            for (String sentence : sentences) {
                List<String> words = fmm(sentence, dict, 7);
                for (String word : words) {
                    bw.write(word + "/");
                }
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void version2(){
        // 读取corpus.dict.txt文件 - 所有词汇读入dict列表，按单词长度，分成多个字典
        HashMap<Integer, List<String>> dictMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("corpus.dict.txt"))){
            String line;
            while ((line = br.readLine()) != null) {
                int len = line.length();
                if (!dictMap.containsKey(len)) {
                    dictMap.put(len, new ArrayList<>());
                }
                dictMap.get(len).add(line);
            }
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
        // 开始计时（用最精确的计时方法）
        long start = System.nanoTime();
        for (String sentence : sentences) {
            List<String> words = fmm(sentence, dictMap, 7);
        }
        // 结束计时
        long end = System.nanoTime();
        System.out.println("Version 2 (Sub-dicts): Time used: " + (end - start) / 1000000000.0 + "s");

        // 将结果写入文件-用于验证
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("timetest_v2.txt", false))){
            // 打印分词结果到result.txt文件
            for (String sentence : sentences) {
                List<String> words = fmm(sentence, dictMap, 7);
                for (String word : words) {
                    bw.write(word + "/");
                }
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void version3(){
        // 读取corpus.dict.txt文件 - 所有词汇读入dict前缀树
        Trie dict = new Trie();
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
        // 开始计时（用最精确的计时方法）
        long start = System.nanoTime();
        for (String sentence : sentences) {
            List<String> words = fmm(sentence, dict, 7);
        }
        // 结束计时
        long end = System.nanoTime();
        System.out.println("Version 3 (Trie Tree): Time used: " + (end - start) / 1000000000.0 + "s");

        // 将结果写入文件-用于验证
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("timetest_v3.txt", false))){
            // 打印分词结果到result.txt文件
            for (String sentence : sentences) {
                List<String> words = fmm(sentence, dict, 7);
                for (String word : words) {
                    bw.write(word + "/");
                }
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
