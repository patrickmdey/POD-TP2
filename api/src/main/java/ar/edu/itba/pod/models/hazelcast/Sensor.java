package ar.edu.itba.pod.models.hazelcast;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

import java.io.IOException;
import java.io.Serializable;

public class Sensor implements Serializable {
    private int id;
    private String description;
    private Status status;

    public Sensor() {}

    public Sensor(int id, String description, Status status) {
        this.id = id;
        this.description = description;
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }

    public int getId() {
        return id;
    }

    public boolean isActive() {
        return status.equals(Status.A);
    }

//    @Override
//    public void writeData(ObjectDataOutput out) throws IOException {
//        out.writeInt(id);
//        out.writeUTF(description);
//        out.writeInt(status.ordinal());
//    }
//
//    @Override
//    public void readData(ObjectDataInput in) throws IOException {
//        id = in.readInt();
//        description = in.readUTF();
//        status = Status.values()[in.readInt()];
//    }

}
