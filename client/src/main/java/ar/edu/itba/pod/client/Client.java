package ar.edu.itba.pod.client;

import ar.edu.itba.pod.collators.*;
import ar.edu.itba.pod.mappers.*;
import ar.edu.itba.pod.models.Constants;
import ar.edu.itba.pod.models.TopSensorMonth;
import ar.edu.itba.pod.models.TotalReadingSensor;
import ar.edu.itba.pod.models.Utils;
import ar.edu.itba.pod.models.hazelcast.Reading;
import ar.edu.itba.pod.models.responses.MaxSensorResponse;
import ar.edu.itba.pod.models.responses.MillionsPairResponse;
import ar.edu.itba.pod.models.responses.YearCountResponse;
import ar.edu.itba.pod.reducers.*;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IList;
import com.hazelcast.core.IMap;
import com.hazelcast.mapreduce.Job;
import com.hazelcast.mapreduce.JobTracker;
import com.hazelcast.mapreduce.KeyValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class Client {
    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

    // TODO: falta hacer un combiner
    // TODO: fijarse ints y longs

    public static void main(String[] args) {
        LOGGER.info("tpe2-g6-parent Client Starting ...");

        ClientParser parser = new ClientParser();
        parser.parse();

        HazelcastInstance hz = Utils.getHazelcastInstance();

        try (FileWriter fw = new FileWriter("time" + parser.getQuery() + ".txt", false)) {
            // TODO: formato del LocalDate
            fw.write(LocalDateTime.now() + " - Inicio de lectura del archivo\n");
            Utils.parseReadings(parser.getInPath(), hz);
            Utils.parseSensorsData(parser.getInPath(), hz);
            fw.write(LocalDateTime.now() + " - Fin de lectura del archivo\n");


            IList<Reading> readingIList = hz.getList(Constants.READINGS_MAP);
            KeyValueSource<String, Reading> source = KeyValueSource.fromList(readingIList);

            fw.write(LocalDateTime.now() + " - Inicio del trabajo map/reduce\n");
            try {
                switch (parser.getQuery()) {
                    case 1:
                        runQuery1(parser.getOutPath(), hz, source);
                        break;
                    case 2:
                        runQuery2(parser.getOutPath(), hz, source);
                        break;
                    case 3:
                        runQuery3(parser.getOutPath(), hz, source, parser.getMin());
                        break;
                    case 4:
                        runQuery4(parser.getOutPath(), hz, source, parser.getYear(), parser.getN());
                        break;
                    case 5:
                        runQuery5(parser.getOutPath(), hz, source);
                        break;
                }
                fw.write(LocalDateTime.now() + " - Inicio del trabajo map/reduce");
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                LOGGER.error("Future job wasn't able to finish");
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("Couldn't write to file");
        }

        HazelcastClient.shutdownAll();
    }

    public static void runQuery1(String outPath, HazelcastInstance hz, KeyValueSource<String, Reading> source) throws InterruptedException, ExecutionException {
        JobTracker t = hz.getJobTracker("query-1");
        Job<String, Reading> job = t.newJob(source);

        ICompletableFuture<Collection<TotalReadingSensor>> future = job.mapper(new ReadingNameMapper())
                .reducer(new ReadingCountReducerFactory()).submit(new TotalReadingCollator());

        Collection<TotalReadingSensor> result = future.get();

        QueryResponseWriter.writeQueryResponse(outPath + "/query1.csv", result,
                new String[]{"Sensor", "Total_Count"});
    }

    public static void runQuery2(String outPath, HazelcastInstance hz, KeyValueSource<String, Reading> source) throws InterruptedException, ExecutionException {
        JobTracker t = hz.getJobTracker("query-2");
        Job<String, Reading> job = t.newJob(source);

        ICompletableFuture<Collection<YearCountResponse>> future = job.mapper(new ReadingDateTypeMapper())
                .reducer(new CountPerDateTypeReducerFactory())
                .submit(new OrderByYearCollator());

        Collection<YearCountResponse> result = future.get();

        QueryResponseWriter.writeQueryResponse(outPath + "/query2.csv", result,
                new String[]{"Year", "Weekdays_Count", "Weekends_Count", "Total_Count"});
    }

    public static void runQuery3(String outPath, HazelcastInstance hz, KeyValueSource<String, Reading> source, long minValue) throws InterruptedException, ExecutionException {
        JobTracker t = hz.getJobTracker("query-3");
        Job<String, Reading> job = t.newJob(source);

        ICompletableFuture<Collection<MaxSensorResponse>> future = job.mapper(
                new MaxReadingMapper(minValue))
                .reducer(new MaxReadingReducerFactory())
                .submit(new MaxReadingCollator());

        Collection<MaxSensorResponse> result = future.get();
        QueryResponseWriter.writeQueryResponse(outPath + "/query3.csv", result,
                new String[]{"Sensor", "Max_Reading_Count", "Max_Reading_DateTime"});
    }

    public static void runQuery4(String outPath, HazelcastInstance hz, KeyValueSource<String, Reading> source, int year, int topAmount) throws InterruptedException, ExecutionException {
        JobTracker t = hz.getJobTracker("query-4");
        Job<String, Reading> job = t.newJob(source);

        // TODO: chequear #nomenclatura
        ICompletableFuture<Collection<TopSensorMonth>> future = job.mapper(
                new TopAverageMonthMapper(year))
                .reducer(new TopAverageMonthReducerFactory())
                .submit(new TopAverageMonthCollator(topAmount));

        Collection<TopSensorMonth> result = future.get();

        QueryResponseWriter.writeQueryResponse(outPath + "/query4.csv", result,
                new String[]{"Sensor", "Month", "Max_Monthly_Avg"});

    }

    public static void runQuery5(String outPath, HazelcastInstance hz, KeyValueSource<String, Reading> rawSource) throws ExecutionException, InterruptedException {
        JobTracker t = hz.getJobTracker("query-5");
        Job<String, Reading> countingJob = t.newJob(rawSource);

        ICompletableFuture<Map<String, Long>> futureCounted = countingJob.mapper(new ReadingNameMapper())
                .reducer(new ReadingCountReducerFactory())
                .submit();

        Map<String, Long> countedResult = futureCounted.get();
        IMap<String, Long> countedResultHz = hz.getMap(Constants.SENSORS_COUNT_MAP);
        countedResultHz.putAll(countedResult);

        KeyValueSource<String, Long> countedSource = KeyValueSource.fromMap(countedResultHz);
        Job<String, Long> groupingJob = t.newJob(countedSource);

        ICompletableFuture<Collection<MillionsPairResponse>> futureGrouped = groupingJob
                .mapper(new GroupingMillionsMapper())
                .reducer(new GroupingMillionsReducerFactory())
                .submit(new MillionsPairCollator());

        Collection<MillionsPairResponse> groupedResult = futureGrouped.get();

        QueryResponseWriter.writeQueryResponse(outPath + "/query5.csv", groupedResult,
                new String[]{"Group", "Sensor A", "Sensor B"});
    }
}
