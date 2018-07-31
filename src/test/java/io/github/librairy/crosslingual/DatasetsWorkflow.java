package io.github.librairy.crosslingual;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.librairy.crosslingual.data.Article;
import io.github.librairy.crosslingual.services.TranslationService;
import io.github.librairy.crosslingual.utils.ArticleUtils;
import io.github.librairy.crosslingual.utils.ReaderUtils;
import io.github.librairy.crosslingual.utils.WriterUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */

public class DatasetsWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetsWorkflow.class);


    static List<String> LANGUAGES = Arrays.asList(new String[]{"en","es","fr","de"});

    static Integer TRAINING_SIZE = 4000;

    static Integer TEST_SIZE = 1000;


    public static void main(String[] args) throws IOException {


        try{
            Properties settings = new Properties();
            settings.load(new FileInputStream("src/test/resources/settings.properties"));


            BufferedReader reader = ReaderUtils.from(settings.getProperty("articles.path"));


            // initialize translator
            TranslationService translator = new TranslationService();

//         Create writers
            Map<String,BufferedWriter> writers = new ConcurrentHashMap<>();
            for (int i=1;i<=LANGUAGES.size();i++){

                String baseTest = "exp"+i;

                for (int j=1;j<=i;j++){

                    List<String> modes = Arrays.asList(new String[]{"train","test","testIDs"});

                    for (String mode: modes){
                        String id = baseTest+"_"+mode+"_"+LANGUAGES.get(j-1);
                        File outputFile = new File(settings.getProperty("corpus.path")+"/"+ id + ".json.gz");
                        if (outputFile.exists()) outputFile.delete();
                        else outputFile.getParentFile().mkdirs();
                        try {
                            LOG.info("Writer initialized: " + id);
                            writers.put(id, WriterUtils.to(outputFile.getAbsolutePath()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }


            ObjectMapper jsonMapper = new ObjectMapper();

            Map<String,String> testingArticles = new ConcurrentHashMap<>();
            Map<String,List<String>> testingReferences = new ConcurrentHashMap<>();
            AtomicInteger testCounter = new AtomicInteger();
            AtomicInteger testReferencesCounter = new AtomicInteger();
            String articleJson;

            // Create Test Set
            while ((articleJson = reader.readLine()) != null){

                if ((testCounter.get() >= TEST_SIZE)) break;

                Article article = jsonMapper.readValue(articleJson, Article.class);

                if (!article.getLanguage().equalsIgnoreCase(LANGUAGES.get(0))) continue;

                if (!ArticleUtils.isValid(article) || !ArticleUtils.isValidAsTest(article)) continue;

                // candidate for testing
                for (int i=1;i<=LANGUAGES.size();i++){
                    String test = "exp"+i;

                    int maxSize = Double.valueOf(Math.ceil(Double.valueOf(TEST_SIZE) / Double.valueOf(i))).intValue();

                    int targetLangIndex = testCounter.get() / maxSize;
                    String targetLang = LANGUAGES.get(targetLangIndex);

                    String writerId = test+"_test_"+targetLang;
                    String writerIds = test+"_testIDs_"+targetLang;


                    if (targetLangIndex > 0){
                        Article translation = translator.translate(article, targetLang);
                        article = translation;
                    }

                    LOG.info("Adding new testing article: " + article.getId() + " to: " + writerId + "[" + testCounter.get() + "]");
                    writers.get(writerId).write(jsonMapper.writeValueAsString(article)+"\n");
                    writers.get(writerIds).write(article.getId()+"\n");
                    testingArticles.put(article.getId(),writerId);
                    article.getCitedBy().forEach(rid -> {
                        List<String> artWriters = new ArrayList<String>();
                        if (testingReferences.containsKey(rid)){
                            artWriters = testingReferences.get(rid);
                        }
                        artWriters.add(writerId);
                        testingReferences.put(rid,artWriters);
                    });
                }
                testCounter.incrementAndGet();


            }

            reader.close();
            reader = ReaderUtils.from(settings.getProperty("articles.path"));

            // Create Training Set
            AtomicInteger trainingCounter = new AtomicInteger();
            while ((articleJson = reader.readLine()) != null){

                if ((trainingCounter.get() >= TRAINING_SIZE)) break;

                Article article = jsonMapper.readValue(articleJson, Article.class);

                if (!article.getLanguage().equalsIgnoreCase(LANGUAGES.get(0))) continue;

                if (!ArticleUtils.isValid(article)) continue;

                if (testingArticles.containsKey(article.getId())) continue;

                if (testingReferences.containsKey(article.getId())){
                    // Translate?
                    testReferencesCounter.incrementAndGet();
                    List<String> writersId = testingReferences.get(article.getId());
                    for(String writerId : writersId){
                        String newWriterId = StringUtils.substringBeforeLast(writerId, "_") + "_" + LANGUAGES.get(0);
                        LOG.info("Adding new testing reference: " + article.getId() + " to: " + newWriterId);
                        writers.get(newWriterId).write(jsonMapper.writeValueAsString(article)+"\n");
                    }
                }else{
                    for (int i=1;i<=LANGUAGES.size();i++){
                        String test = "exp"+i;

                        int maxSize = Double.valueOf(Math.ceil(Double.valueOf(TRAINING_SIZE ) / Double.valueOf(i))).intValue();

                        int targetLangIndex = trainingCounter.get() / maxSize;
                        String targetLang = LANGUAGES.get(targetLangIndex);

                        String writerId = test+"_train_"+targetLang;

                        if (targetLangIndex > 0){
                            Article translation = translator.translate(article, targetLang);
                            article = translation;
                        }

                        LOG.info("Adding new training article: " + article.getId() + " to: " + writerId);
                        writers.get(writerId).write(jsonMapper.writeValueAsString(article)+"\n");

                    }
                    trainingCounter.incrementAndGet();
                }
            }

            // Complete Test Set

            for(Map.Entry<String,List<String>> entry: testingReferences.entrySet()){
                String articleId = entry.getKey();
                List<String> writersId  = entry.getValue();



//                for(String writerId : writersId){
//                    String newWriterId = StringUtils.substringBeforeLast(writerId, "_") + "_" + LANGUAGES.get(0);
//                    LOG.info("Adding new testing reference: " + article.getId() + " to: " + newWriterId);
//                    writers.get(newWriterId).write(jsonMapper.writeValueAsString(article)+"\n");
//                }


            };

            // Close writers
            writers.entrySet().stream().forEach(entry -> {
                try {
                    entry.getValue().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            LOG.info("Corpus created successfully! [training:" + trainingCounter.get() + "/test:" + testCounter.get()+"] + testing references [" + testingReferences.size() + "/" + testReferencesCounter.get() + "]");



        }catch (Exception e){
            LOG.error("Unexpected global error", e);
        }

    }



}
