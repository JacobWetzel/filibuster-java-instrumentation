package algorithmjacob.jacobservices;
import cloud.filibuster.examples.AGrpc;
import cloud.filibuster.examples.BGrpc;
import cloud.filibuster.examples.Jacobalg;
import cloud.filibuster.instrumentation.helpers.Networking;
import io.grpc.stub.StreamObserver;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


public class MyAService extends AGrpc.AImplBase{

    private final MetaDataContainer metadataContainer;
    private static final String metadataPath = "/home/jwetzel/filibuster-java-instrumentation/src/test/java/algorithmjacob/jacobservices/AMetaData.json";


    public MyAService() {
        MetaDataContainer existingData = JsonUtil.readMetaData(metadataPath);
        if (existingData != null) {
            this.metadataContainer = existingData;
        } else {
            this.metadataContainer = new MetaDataContainer();
            this.metadataContainer.setMetaDataMap(new HashMap<>());
            this.metadataContainer.setGeneratedIDs(new ArrayList<>());
            JsonUtil.writeMetaData(this.metadataContainer, metadataPath);
        }
        /*for (HashMap.Entry<Float, JacobMetaData> entry : metadataContainer.getMetaDataMap().entrySet()) {
            if(entry.getValue().Completed = false){
                appendA(entry.getValue().req, entry.getValue().responseObserver);
            }
        }*/
    }

@Override
public void appendA(Jacobalg.AppendRequest req, StreamObserver<Jacobalg.AppendReply> responseObserver) {

    if(metadataContainer.getMetaDataMap().containsKey(req.getCallID())){
        JacobMetaData existingMetaData = metadataContainer.getMetaDataMap().get(req.getCallID());
        if(existingMetaData.retval != null){
            Jacobalg.AppendReply reply = Jacobalg.AppendReply.newBuilder()
                    .setReplyString(existingMetaData.retval)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
            return;
        }

        if(existingMetaData.usedCallIDs.get(0) != -1f){
            callB(existingMetaData.usedCallIDs.get(0), req, responseObserver, existingMetaData);
        }else{
            Float newID = generateNewID(metadataContainer);
            existingMetaData.usedCallIDs.set(0, newID);
            callB(newID, req, responseObserver, existingMetaData);
        }
    }else{
        JacobMetaData newMetaData = new JacobMetaData(1, req);
        Float newID = generateNewID(metadataContainer);
        newMetaData.usedCallIDs.set(0, newID);
        metadataContainer.getMetaDataMap().put(req.getCallID(), newMetaData);
        metadataContainer.setGeneratedIDs(metadataContainer.getGeneratedIDs());
        JsonUtil.writeMetaData(metadataContainer, metadataPath);
        callB(newID, req, responseObserver, newMetaData);
    }
}

    @SuppressWarnings("UnnecessaryBoxedVariable")
    private void callB(Float callID, Jacobalg.AppendRequest req, StreamObserver<Jacobalg.AppendReply> responseObserver, JacobMetaData metaData){
        ManagedChannel BChannel = ManagedChannelBuilder
                .forAddress(Networking.getHost("B"), Networking.getPort("B"))
                .usePlaintext()
                .build();
        BGrpc.BBlockingStub blockingStubB = BGrpc.newBlockingStub(BChannel);

        Jacobalg.AppendRequest request = Jacobalg.AppendRequest.newBuilder()
                .setBaseString(req.getBaseString())
                .setCallID(callID)
                .build();

        Jacobalg.AppendReply reply = blockingStubB.appendB(request);
        BChannel.shutdownNow();
        reply = Jacobalg.AppendReply.newBuilder()
                .setReplyString(reply.getReplyString() + "A")
                .build();
        metaData.retval = reply.getReplyString();
        metaData.Completed = true;
        JsonUtil.writeMetaData(metadataContainer, metadataPath);

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
    public Float generateNewID(MetaDataContainer existingData){
        List<Float> existingIDs = existingData.getGeneratedIDs();
        Float newID;

        if (existingIDs.isEmpty()) {
            newID = 0.5f;
        } else {
            newID = existingIDs.get(existingIDs.size() - 1) + 1;
        }

        existingIDs.add(newID);
        existingData.setGeneratedIDs(existingIDs);
        JsonUtil.writeMetaData(existingData, metadataPath);
        return newID;
    }

}
