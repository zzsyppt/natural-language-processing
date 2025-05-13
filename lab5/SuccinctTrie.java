import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SuccinctTrie {

    static class Louds {
        BitSet bits;
        byte[] labels;
        int bitLen;
    }

    public static void main(String[] args) throws IOException {
        List<String> vocab = readVocab("dict.txt");
        System.out.printf("读取词典：%d 个词项\n", vocab.size());

        Louds louds = buildLouds(vocab);
        System.out.printf("LOUDS 构建完成，位串长度=%d，标签数=%d\n", louds.bitLen, louds.labels.length);
        // 保存到文件
        saveLouds(louds, "COMPRESSED.bin");
        int[] ptrs = buildNodeIdPointers(vocab, louds);
        System.out.printf("节点指针构建完成，指针数=%d\n", ptrs.length);

        long bitsBytes = (louds.bitLen + 7) / 8;
        long labelsBytes = louds.labels.length;
        long ptrBytes = ptrs.length * 4;

        long totalBytes = bitsBytes + labelsBytes + ptrBytes;

        System.out.printf("bits=%,d bytes, labels=%,d bytes, ptrs=%,d bytes\n", bitsBytes, labelsBytes, ptrBytes);
        System.out.printf("总共=%,d bytes (≈%.2f KB)\n", totalBytes, totalBytes / 1024.0);




        // 随机查询示例
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            int idx = rand.nextInt(vocab.size());
            String original = vocab.get(idx);
            int nodeId = ptrs[idx];
            String recovered = toTerm(louds, nodeId);
            System.out.printf("原词：%-10s  → 查询结果：%s\n", original, recovered);
        }
    }

    private static List<String> readVocab(String file) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
        return Arrays.asList(content.split("\\s+"));
    }

    private static Louds buildLouds(List<String> vocab) {
        BitSet bits = new BitSet();
        ByteArrayOutputStream labStream = new ByteArrayOutputStream();

        Queue<Node> queue = new ArrayDeque<>();
        Node root = new Node();
        for (String w : vocab) insert(root, w);

        queue.add(root);
        int bitPos = 0;

        while (!queue.isEmpty()) {
            Node p = queue.poll();
            List<Character> keys = new ArrayList<>(p.children.keySet());
            Collections.sort(keys); //可以不排序

            for (char c : keys) {
                bits.set(bitPos);  // 子边为1
                Node child = p.children.get(c);
                queue.add(child);
                labStream.write((byte) c);
                bitPos++;
            }
            bitPos++;  // 结束0
        }

        Louds l = new Louds();
        l.bits = bits;
        l.bitLen = bitPos;
        l.labels = labStream.toByteArray();
        return l;
    }

    static class Node {
        Map<Character, Node> children = new HashMap<>();
    }

    private static void insert(Node root, String word) {
        Node cur = root;
        for (char c : word.toCharArray()) {
            cur = cur.children.computeIfAbsent(c, k -> new Node());
        }
    }

    private static int[] buildNodeIdPointers(List<String> vocab, Louds l) {
        int[] ptr = new int[vocab.size()];

        for (int wi = 0; wi < vocab.size(); wi++) {
            String w = vocab.get(wi);
            int nodeId = 0;

            for (char c : w.toCharArray()) {
                //叫startbit更好些  根节点有特例
                int childStart = (nodeId == 0) ? 0 : select0(l.bits, nodeId - 1) + 1;
                int nextNodeId = -1;

                for (int bitIdx = childStart; bitIdx < l.bitLen && l.bits.get(bitIdx); bitIdx++) {
                    int childNodeId = rank1(l.bits, bitIdx);
                    if (l.labels[childNodeId - 1] == c) {
                        nextNodeId = childNodeId;
                        break;
                    }
                }

                if (nextNodeId == -1) throw new RuntimeException("词典不一致：" + w);
                nodeId = nextNodeId;
            }

            ptr[wi] = nodeId;
        }

        return ptr;
    }

    private static String toTerm(Louds l, int nodeId) {
        StringBuilder sb = new StringBuilder();

        while (nodeId > 0) {
            sb.append((char) l.labels[nodeId - 1]);
            nodeId = rank0(l.bits, select1(l.bits, nodeId - 1));
        }

        return sb.reverse().toString();
    }

    // 工具函数：rank1, rank0, select1, select0
    private static int rank1(BitSet bs, int pos) {
        return bs.get(0, pos + 1).cardinality();
    }

    private static int rank0(BitSet bs, int pos) {
        return (pos + 1) - rank1(bs, pos);
    }

    private static int select1(BitSet bs, int k) {
        int count = 0;
        for (int i = 0; i < bs.length(); i++) {
            if (bs.get(i)) {
                if (count == k) return i;
                count++;
            }
        }
        return -1;
    }

    private static int select0(BitSet bs, int k) {
        int count = 0;
        for (int i = 0; i < bs.length(); i++) {
            if (!bs.get(i)) {
                if (count == k) return i;
                count++;
            }
        }
        return -1;
    }

    private static void saveLouds(Louds l, String file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(l.bitLen);

            // bits保存为byte数组（BitSet.toByteArray是小端序）
            byte[] bitBytes = l.bits.toByteArray();
            out.writeInt(bitBytes.length);  // 记录bitBytes长度
            out.write(bitBytes);

            // labels保存
            out.writeInt(l.labels.length);  // 记录labels长度
            out.write(l.labels);
        }
        System.out.println("LOUDS 压缩词典已保存到 " + file);
    }

}
