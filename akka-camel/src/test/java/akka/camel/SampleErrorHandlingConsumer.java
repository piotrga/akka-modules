package akka.camel;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.camel.javaapi.ConsumerConfigBuilder;
import akka.camel.javaapi.UntypedConsumerActor;
import akka.util.Duration;
import org.apache.camel.builder.Builder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;

/**
 * @author Martin Krasser
 */
public class SampleErrorHandlingConsumer extends UntypedConsumerActor {

    public String getEndpointUri() {
        return "direct:error-handler-test-java";
    }

    @Override
    public ConsumerConfig config(){
        return new ConsumerConfigBuilder(){
            @Override
            //TODO write test confirming this gets called in java
            public ProcessorDefinition<?> onRouteDefinition(RouteDefinition rd) {
                return rd.onException(Exception.class).handled(true).transform(Builder.exceptionMessage()).end();
            }

        }.withBlocking(new Blocking(Duration.fromNanos(100000000000L))).build();
    }

    public void onReceive(Object message) throws Exception {
        Message msg = (Message)message;
        String body = msg.getBodyAs(String.class);
        throw new Exception(String.format("error: %s", body));
    }

    public static void main(String[] args) {
        ActorSystem.create("test").actorOf(new Props().withCreator( SampleErrorHandlingConsumer.class));
    }

}
