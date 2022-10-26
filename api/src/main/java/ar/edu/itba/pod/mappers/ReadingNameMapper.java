package ar.edu.itba.pod.mappers;

import ar.edu.itba.pod.models.Constants;
import ar.edu.itba.pod.models.hazelcast.Reading;
import ar.edu.itba.pod.models.hazelcast.Sensor;
import ar.edu.itba.pod.models.hazelcast.Status;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.mapreduce.Context;
import com.hazelcast.mapreduce.Mapper;

import java.util.Map;

public class ReadingNameMapper implements Mapper<String, Reading, String, Long> {
//        HazelcastInstanceAware {

//    private transient HazelcastInstance hz;



    private final Map<Integer, Sensor> sensorMap;

    public ReadingNameMapper(Map<Integer, Sensor> sensorMap) {
        this.sensorMap = sensorMap;
    }

    @Override
    public void map(String key, Reading value, Context<String, Long> context) {
        Sensor sensor = sensorMap.get(value.getSensorId());
        if (sensor.getStatus().equals(Status.A))
            context.emit(sensor.getDescription(), value.getHourlyCounts());
    }

//    @Override
//    public void setHazelcastInstance(HazelcastInstance hz) {
//        this.hz = hz;
//    }
}
