package ar.edu.itba.pod;

import ar.edu.itba.pod.assets.TestConstants;
import ar.edu.itba.pod.collators.TotalReadingCollator;
import ar.edu.itba.pod.mappers.ReadingNameMapper;
import ar.edu.itba.pod.models.Constants;
import ar.edu.itba.pod.models.hazelcast.*;
import ar.edu.itba.pod.models.responses.TotalReadingSensor;
import ar.edu.itba.pod.reducers.ReadingCountReducerFactory;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IList;
import com.hazelcast.core.IMap;
import com.hazelcast.mapreduce.Job;
import com.hazelcast.mapreduce.JobTracker;
import com.hazelcast.mapreduce.KeyValueSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Query1Test {

    private TestHazelcastFactory factory;
    private HazelcastInstance member, client;

    @Before
    public void before() {
        factory = new TestHazelcastFactory();

        // Config
        GroupConfig groupConfig = new GroupConfig()
                .setName("g6")
                .setPassword("g6-pass");

        // Start cluster
        Config config = new Config().setGroupConfig(groupConfig);
        member = factory.newHazelcastInstance(config);

        // CLient config
        ClientConfig clientConfig = new ClientConfig().setGroupConfig(groupConfig);
        client = factory.newHazelcastClient(clientConfig);
    }

    @Test
    public void query1TestWithoutCombiner() throws ExecutionException, InterruptedException {
        IList<Reading> readingIList = client.getList(Constants.READINGS_MAP);
        readingIList.addAll(TestConstants.READINGS);

        IMap<Integer, Sensor> sensorIMap = client.getMap(Constants.SENSORS_MAP);
        sensorIMap.putAll(TestConstants.SENSORS);

        final KeyValueSource<String, Reading> source = KeyValueSource.fromList(readingIList);

        JobTracker jobTracker = client.getJobTracker("query-1-test");
        Job<String, Reading> job = jobTracker.newJob(source);

        ICompletableFuture<Collection<TotalReadingSensor>> future = job
                .mapper(new ReadingNameMapper(TestConstants.SENSORS))
                .reducer(new ReadingCountReducerFactory())
                .submit(new TotalReadingCollator());

        Collection<TotalReadingSensor> actualValue = future.get();

        assertEquals(2, actualValue.size());
        assertEquals(new TotalReadingSensor(TestConstants.SENSOR_1_NAME, 98000), actualValue.toArray()[0]);
        assertEquals(new TotalReadingSensor(TestConstants.SENSOR_2_NAME, 49000), actualValue.toArray()[1]);
    }

    @Test
    public void query1TestWithNoActiveSensor() throws ExecutionException, InterruptedException {
        IList<Reading> readingIList = client.getList(Constants.READINGS_MAP);
        readingIList.addAll(TestConstants.READINGS);

        IMap<Integer, Sensor> sensorIMap = client.getMap(Constants.SENSORS_MAP);
        sensorIMap.putAll(TestConstants.NONACTIVE_SENSORS);

        final KeyValueSource<String, Reading> source = KeyValueSource.fromList(readingIList);

        JobTracker jobTracker = client.getJobTracker("query-1-test");
        Job<String, Reading> job = jobTracker.newJob(source);

        ICompletableFuture<Collection<TotalReadingSensor>> future = job
                .mapper(new ReadingNameMapper(TestConstants.NONACTIVE_SENSORS))
                .reducer(new ReadingCountReducerFactory())
                .submit(new TotalReadingCollator());

        Collection<TotalReadingSensor> actualValue = future.get();

        assertTrue(actualValue.isEmpty());
    }

    @After
    public void after() {
        factory.shutdownAll();
    }
}
