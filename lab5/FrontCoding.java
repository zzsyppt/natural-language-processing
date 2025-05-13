import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FrontCoding {

    /* ===== CLI 入口 ===== */
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法:\n  encode <inTxt> <outBin> [k]\n  decode <inBin> <outTxt>");
            return;
        }
        String mode = args[0];
        if ("encode".equalsIgnoreCase(mode)) {
            int k = (args.length >= 4) ? Integer.parseInt(args[3]) : 4;
            List<String> terms = readTerms(Paths.get(args[1]));
            byte[] bytes = compress(terms, k);
            Files.write(Paths.get(args[2]), bytes);
            System.out.printf("压缩完成：原 %d 个词 → %d 字节，块大小 k=%d%n",
                    terms.size(), bytes.length, k);
        } else if ("decode".equalsIgnoreCase(mode)) {
            byte[] bytes = Files.readAllBytes(Paths.get(args[1]));
            List<String> terms = decompress(bytes);
            Files.write(Paths.get(args[2]), String.join("\n", terms).getBytes(StandardCharsets.UTF_8));
            System.out.printf("解压完成：恢复 %d 个词%n", terms.size());
        } else {
            System.err.println("未知模式：" + mode);
        }
    }

    /* ===== 压缩 ===== */
    public static byte[] compress(List<String> terms, int k) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 在文件开头写入块大小 k
        baos.write(k);

        int i = 0;
        while (i < terms.size()) {
            // 每个块的实际大小
            int blockSize = Math.min(k, terms.size() - i);

            // 计算块内所有词的公共前缀
            String firstTerm = terms.get(i);
            int lcp = firstTerm.length();  // 初始化为第一个词的长度

            // 遍历当前块内所有词，计算公共前缀
            for (int j = 1; j < blockSize; j++) {
                lcp = Math.min(lcp, longestCommonPrefix(firstTerm, terms.get(i + j)));
            }

            // 写入公共前缀的长度，只写一次
            baos.write(lcp);

            // 写入第一个词的内容（完整存储）
            byte[] firstBytes = terms.get(i).getBytes(StandardCharsets.UTF_8);
            baos.write(firstBytes.length);  // 写入第一个词的长度
            baos.write(firstBytes);         // 写入第一个词

            // 写入块内其他词的后缀部分
            for (int j = 1; j < blockSize; j++) {
                String term = terms.get(i + j);
                // 获取当前词的后缀部分（从公共前缀之后的部分）
                String suffix = term.substring(lcp);
                byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);

                // 只写公共前缀的长度一次，然后写每个词的后缀部分
                baos.write(suffixBytes.length); // 后缀部分的长度
                baos.write(suffixBytes);         // 后缀内容
            }

            // 更新索引
            i += blockSize;
        }

        return baos.toByteArray();
    }




    /* ===== 解压（JDK 8 版） ===== */
    public static List<String> decompress(byte[] data) throws IOException {
        List<String> result = new ArrayList<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        // 1. 读取块大小 k
        int k = in.readUnsignedByte();  // 读取块大小

        while (in.available() > 0) {
            // 2. 读取每个块的大小，块大小为 k 或最后一个块的剩余大小
            int blockSize = Math.min(k, in.available());  // 读取当前块的实际大小

            // 3. 读取公共前缀长度
            int lcp = in.readUnsignedByte();  // 读取公共前缀长度

            // 4. 读取第一个词的长度
            int firstLen = in.readUnsignedByte();  // 读取第一个词的长度

            // 5. 读取第一个词
            byte[] firstBytes = new byte[firstLen];
            in.readFully(firstBytes);  // 读取第一个词
            String first = new String(firstBytes, StandardCharsets.UTF_8);
            result.add(first);  // 添加第一个词到结果列表

            // 6. 按照块大小继续读取后续词的后缀部分
            for (int j = 1; j < blockSize; j++) {
                // 读取后缀部分的长度
                int sufLen = in.readUnsignedByte();

                // 读取后缀内容
                byte[] sufBytes = new byte[sufLen];
                in.readFully(sufBytes);  // 读取后缀部分

                // 恢复当前词：将公共前缀与后缀拼接起来
                result.add(first.substring(0, lcp) + new String(sufBytes, StandardCharsets.UTF_8));
            }

            // 继续处理下一个块
        }

        return result;
    }






    /* ===== 工具函数 ===== */
    private static List<String> readTerms(Path path) throws IOException {
        // JDK 8 没有 Files.readString，用 readAllBytes 替代
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return Arrays.asList(content.split("\\s+"));
    }

    private static int longestCommonPrefix(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }
}
