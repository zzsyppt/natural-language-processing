import com.huaban.analysis.jieba.JiebaSegmenter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    static class Document {
        int id;
        String content;
        Map<String, Integer> termFreq = new HashMap<>();
        Map<String, Double> tfidf = new HashMap<>();
        double norm = 0.0;

        public Document(int id, String content, Set<String> stopWords, JiebaSegmenter segmenter) {
            this.id = id;
            this.content = content;
            List<String> tokens = segmenter.sentenceProcess(content);
            for (String token : tokens) {
                token = token.trim();
                if (!stopWords.contains(token) && !token.isEmpty()) {
                    termFreq.put(token, termFreq.getOrDefault(token, 0) + 1);
                }
            }
        }

        public void computeTFIDF(Map<String, Integer> dfMap, int totalDocs) {
            double sumSq = 0;
            for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
                String term = entry.getKey();
                int tf = entry.getValue();
                int df = dfMap.getOrDefault(term, 1);
                double idf = Math.log((double) totalDocs / df);
                double weight = (1 + Math.log(tf)) * idf;
                tfidf.put(term, weight);
                sumSq += weight * weight;
            }
            norm = Math.sqrt(sumSq);
        }
    }

    static class Corpus {
        List<Document> documents = new ArrayList<>();
        Map<String, Integer> dfMap = new HashMap<>();
        Set<String> vocabulary = new HashSet<>();

        public void loadDocuments(String folderPath, Set<String> stopWords, JiebaSegmenter segmenter) throws IOException {
            File folder = new File(folderPath);
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (file.getName().endsWith(".txt")) {
                    int id = Integer.parseInt(file.getName().replace(".txt", ""));
                    // 替代：
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    Document doc = new Document(id, content, stopWords, segmenter);
                    documents.add(doc);
                    for (String term : doc.termFreq.keySet()) {
                        dfMap.put(term, dfMap.getOrDefault(term, 0) + 1);
                        vocabulary.add(term);
                    }
                }
            }
            for (Document doc : documents) {
                doc.computeTFIDF(dfMap, documents.size());
            }
        }

        public List<Document> getDocuments() {
            return documents;
        }

        public Map<String, Integer> getDfMap() {
            return dfMap;
        }
    }

    static class InvertedIndex {
        Map<String, List<Integer>> championList = new HashMap<>();

        public void build(Corpus corpus, int r) {
            Map<String, List<Document>> posting = new HashMap<>();
            // 建立倒排列表（词项 -> 包含该词项的文档列表）
            for (Document doc : corpus.documents) {
                for (Map.Entry<String, Integer> entry : doc.termFreq.entrySet()) {
                    String term = entry.getKey();
                    posting.computeIfAbsent(term, k -> new ArrayList<>()).add(doc);
                }
            }
            // 构建胜者表：每个词项保留 TF 值最高的前 r 个文档
            for (final String term : posting.keySet()) {
                PriorityQueue<Document> heap = new PriorityQueue<>(new Comparator<Document>() {
                    public int compare(Document a, Document b) {
                        return Integer.compare(a.termFreq.get(term), b.termFreq.get(term)); // 小顶堆，TF小的在上
                    }
                });
                // 仅保留 TF 最大的前 r 个文档
                for (Document doc : posting.get(term)) {
                    heap.offer(doc);
                    if (heap.size() > r) {
                        heap.poll(); // 弹出 TF 最小的
                    }
                }
                // 结果从大到小排序
                List<Integer> topR = new ArrayList<>();
                while (!heap.isEmpty()) {
                    topR.add(heap.poll().id); // 注意是倒序，需要反转
                }
                Collections.reverse(topR);
                championList.put(term, topR);
            }
        }

    }


    static class QueryVector {
        final Map<String, Double> vec = new HashMap<>();
        final double norm;

        QueryVector(String text, Corpus corpus,
                    Set<String> stop, JiebaSegmenter seg) {

            /* 1️⃣ 分词+去停用词 → 词频 */
            Map<String,Integer> tf = new HashMap<>();
            for (String tok : seg.sentenceProcess(text)) {
                if (!stop.contains(tok) && !tok.trim().isEmpty()) {
                    tf.put(tok, tf.getOrDefault(tok, 0) + 1);
                }
            }

            /* 2️⃣ TF-IDF 权重 */
            int N = corpus.getDocuments().size();
            Map<String,Integer> dfMap = corpus.getDfMap();
            double sumSq = 0.0;

            for (Map.Entry<String,Integer> e : tf.entrySet()) {
                String term = e.getKey();
                int    tfv  = e.getValue();
                int    df   = dfMap.getOrDefault(term, 1);
                double idf  = Math.log((double) N / df);
                double w    = (1 + Math.log(tfv)) * idf;

                vec.put(term, w);
                sumSq += w * w;
            }
            norm = Math.sqrt(sumSq);
        }
    }


    static class QueryProcessor {
        Corpus corpus;
        InvertedIndex index;
        Set<String> stopWords;
        JiebaSegmenter segmenter;

        public QueryProcessor(Corpus corpus, InvertedIndex index, Set<String> stopWords, JiebaSegmenter segmenter) {
            this.corpus = corpus;
            this.index = index;
            this.stopWords = stopWords;
            this.segmenter = segmenter;
        }

        public List<Map.Entry<Integer, Double>> query(String queryText, int k) {
            // 构建统一的查询向量
            QueryVector qv = new QueryVector(queryText, corpus, stopWords, segmenter);

            // 胜者表候选集：包含任意一个查询词的 top-r 文档
            Set<Integer> candidates = new HashSet<>();
            for (String term : qv.vec.keySet()) {
                List<Integer> docs = index.championList.get(term);
                if (docs != null) {
                    candidates.addAll(docs);
                }
            }

            // 堆维护 Top-K 文档
            PriorityQueue<Map.Entry<Integer, Double>> heap = new PriorityQueue<>(Map.Entry.comparingByValue());
            for (int docId : candidates) {
                Document doc = corpus.documents.stream().filter(d -> d.id == docId).findFirst().orElse(null);
                if (doc == null || doc.norm == 0) continue;

                // 计算余弦相似度
                double dot = 0;
                for (String term : qv.vec.keySet()) {
                    dot += qv.vec.get(term) * doc.tfidf.getOrDefault(term, 0.0);
                }
                double sim = dot / (qv.norm * doc.norm);
                heap.offer(new AbstractMap.SimpleEntry<>(docId, sim));
                if (heap.size() > k) heap.poll();
            }

            // 返回 Top-K 结果，按相似度降序排序
            return heap.stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .collect(Collectors.toList());
        }

    }

    public static Set<String> loadStopWords(String path) throws IOException {
        return Files.readAllLines(Paths.get(path)).stream().map(String::trim).collect(Collectors.toSet());
    }

    static class ExactSearcher {
        private Corpus corpus;
        private Set<String> stopWords;
        private JiebaSegmenter segmenter;

        public ExactSearcher(Corpus corpus, Set<String> stopWords, JiebaSegmenter segmenter) {
            this.corpus = corpus;
            this.stopWords = stopWords;
            this.segmenter = segmenter;
        }

        public List<Map.Entry<Integer, Double>> exactQuery(String query, int k) {
            // 使用统一的查询向量构造器
            QueryVector qv = new QueryVector(query, corpus, stopWords, segmenter);

            // 候选文档：至少包含一个查询词的文档
            List<Document> candidates = new ArrayList<>();
            for (Document doc : corpus.getDocuments()) {
                for (String term : qv.vec.keySet()) {
                    if (doc.termFreq.containsKey(term)) {
                        candidates.add(doc);
                        break;
                    }
                }
            }

            // 堆维护 Top-K
            PriorityQueue<Map.Entry<Integer, Double>> heap = new PriorityQueue<>(
                    Comparator.comparingDouble(Map.Entry::getValue)
            );

            for (Document doc : candidates) {
                if (doc.norm == 0) continue;

                double dot = 0;
                for (String term : qv.vec.keySet()) {
                    dot += qv.vec.get(term) * doc.tfidf.getOrDefault(term, 0.0);
                }
                double sim = dot / (qv.norm * doc.norm);
                heap.offer(new AbstractMap.SimpleEntry<>(doc.id, sim));
                if (heap.size() > k) heap.poll();
            }

            List<Map.Entry<Integer, Double>> result = new ArrayList<>();
            while (!heap.isEmpty()) result.add(heap.poll());
            Collections.reverse(result);
            return result;
        }

    }

    public static void main(String[] args) throws IOException {
        JiebaSegmenter segmenter = new JiebaSegmenter();
        Set<String> stopWords = loadStopWords("StopWords.txt");

        Corpus corpus = new Corpus();
        corpus.loadDocuments("article", stopWords, segmenter);

        InvertedIndex index = new InvertedIndex();
        index.build(corpus, 10); // r

        QueryProcessor processor = new QueryProcessor(corpus, index, stopWords, segmenter);

        ExactSearcher validator = new ExactSearcher(corpus, stopWords, segmenter);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("输入查询：");
            String query = scanner.nextLine().trim();

            System.out.print("输入K值：");
            int k = Integer.parseInt(scanner.nextLine().trim());

            List<Map.Entry<Integer, Double>> result = processor.query(query, k);
            System.out.println("===非精确Top-K：===");
            for (Map.Entry<Integer, Double> entry : result) {
                System.out.printf("文档：%d - 相似度：%.4f\n", entry.getKey(), entry.getValue());
            }

            List<Map.Entry<Integer, Double>> preciseResult = validator.exactQuery(query, k);
            System.out.println("===精确Top-K：===");
            for (Map.Entry<Integer, Double> entry : preciseResult) {
                System.out.printf("文档：%d - 相似度：%.4f\n", entry.getKey(), entry.getValue());
            }
        }
    }
}
