package akka.amqp;

import scala.Option;
import akka.actor.ActorRef;
import akka.actor.Actors;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;

import akka.amqp.rpc.RPC;

import akka.japi.Function;
import akka.japi.Procedure;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"unchecked"})
public class ExampleSessionJava {

    public static void main(String... args) {
        new ExampleSessionJava();
    }

    public ExampleSessionJava() {
        printTopic("DIRECT");
        direct();

        printTopic("CALLBACK");
        callback();

        printTopic("EASY STRING PRODUCER AND CONSUMER");
        easyStringProducerConsumer();

        printTopic("EASY PROTOBUF PRODUCER AND CONSUMER");
        easyProtobufProducerConsumer();

        printTopic("EASY STRING RPC");
        easyStringRpc();

        printTopic("EASY PROTOBUF RPC");
        easyProtobufRpc();

        // postStop everything the amqp tree except the main AMQP supervisor
        // all connections/consumers/producers will be stopped
        AMQP.shutdownAll();

        Actors.registry().shutdownAll();

        printTopic("Happy hAkking :-)");

        System.exit(0);
    }

    private void printTopic(String topic) {

        System.out.println("");
        System.out.println("==== " + topic + " ===");
        System.out.println("");
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException ignore) {
        }
    }

    private void direct() {
        // defaults to amqp://guest:guest@localhost:5672/
        ActorRef connection = AMQP.newConnection();

        AMQP.ExchangeParameters exchangeParameters = new AMQP.ExchangeParameters("my_direct_exchange", Direct.getInstance());

        ActorRef deliveryHandler = Actors.actorOf(DirectDeliveryHandlerActor.class);

        AMQP.ConsumerParameters consumerParameters = new AMQP.ConsumerParameters("some.routing", deliveryHandler, exchangeParameters);
        ActorRef consumer = AMQP.newConsumer(connection, consumerParameters);

        ActorRef producer = AMQP.newProducer(connection, new AMQP.ProducerParameters(exchangeParameters));
        producer.sendOneWay(new Message("@jonas_boner: You sucked!!".getBytes(), "some.routing"));
    }

    private void callback() {

        final CountDownLatch channelCountdown = new CountDownLatch(2);

        ActorRef connectionCallback = Actors.actorOf(ConnectionCallbackActor.class);
        connectionCallback.start();

        AMQP.ConnectionParameters connectionParameters = new AMQP.ConnectionParameters(connectionCallback);
        ActorRef connection = AMQP.newConnection(connectionParameters);

        ActorRef channelCallback = Actors.actorOf(new UntypedActorFactory() {
            public UntypedActor create() {
                return new ChannelCallbackActor(channelCountdown);
            }
        });
        channelCallback.start();

        AMQP.ExchangeParameters exchangeParameters = new AMQP.ExchangeParameters("my_callback_exchange", Direct.getInstance());
        AMQP.ChannelParameters channelParameters = new AMQP.ChannelParameters(channelCallback);

        ActorRef dummyHandler = Actors.actorOf(DummyActor.class);
        AMQP.ConsumerParameters consumerParameters = new AMQP.ConsumerParameters("callback.routing", dummyHandler, exchangeParameters, channelParameters);

        ActorRef consumer = AMQP.newConsumer(connection, consumerParameters);

        ActorRef producer = AMQP.newProducer(connection, new AMQP.ProducerParameters(exchangeParameters, channelParameters));

        // Wait until both channels (producer & consumer) are started before stopping the connection
        try {
            channelCountdown.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
        }
        connection.stop();
    }

    public void easyStringProducerConsumer() {
        ActorRef connection = AMQP.newConnection();

        String exchangeName = "easy.string";

        // listen by default to:
        // exchange = optional exchangeName
        // routingKey = provided routingKey or <exchangeName>.request
        // queueName = <routingKey>.in
        Procedure<String> procedure = new Procedure<String>() {
            public void apply(String message) {
                System.out.println("### >> Received message: " + message);
            }
        };
        AMQP.newStringConsumer(connection, procedure, exchangeName);

        // send by default to:
        // exchange = exchangeName
        // routingKey = <exchange>.request
        AMQP.ProducerClient<String> producer = AMQP.newStringProducer(connection, exchangeName);

        producer.send("This shit is easy!");
    }

    public void easyProtobufProducerConsumer() {

        ActorRef connection = AMQP.newConnection();

        String exchangeName = "easy.protobuf";

        Procedure<AkkaAmqp.TestMessage> procedure = new Procedure<AkkaAmqp.TestMessage>() {
            public void apply(AkkaAmqp.TestMessage message) {
                System.out.println("### >> Received message: " + message);
            }
        };

        AMQP.newProtobufConsumer(connection, procedure, exchangeName, AkkaAmqp.TestMessage.class);

        AMQP.ProducerClient<AkkaAmqp.TestMessage> producerClient = AMQP.newProtobufProducer(connection, exchangeName);

        producerClient.send(AkkaAmqp.TestMessage.newBuilder().setMessage("akka-amqp rocks!").build());
    }

    public void easyStringRpc() {

        ActorRef connection = AMQP.newConnection();

        String exchangeName = "easy.stringrpc";

        // listen by default to:
        // exchange = exchangeName
        // routingKey = <exchange>.request
        // queueName = <routingKey>.in
        RPC.newStringRpcServer(connection, exchangeName, new Function<String, String>() {
            public String apply(String request) {
                System.out.println("### >> Got request: " + request);
                return "Response to: '" + request + "'";
            }
        });

        // send by default to:
        // exchange = exchangeName
        // routingKey = <exchange>.request
        RPC.RpcClient<String, String> stringRpcClient = RPC.newStringRpcClient(connection, exchangeName);

        Option<String> response = stringRpcClient.call("AMQP Rocks!");
        System.out.println("### >> Got response: " + response);

        stringRpcClient.callAsync("AMQP is dead easy", new Procedure<String>() {
            public void apply(String request) {
                System.out.println("### >> This is handled async: " + request);
            }
        });

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException ignore) {
        }
    }


    public void easyProtobufRpc() {

        ActorRef connection = AMQP.newConnection();

        String exchangeName = "easy.protobuf.rpc";

        RPC.newProtobufRpcServer(connection, exchangeName, new Function<AkkaAmqp.TestMessage, AkkaAmqp.TestMessage>() {
            public AkkaAmqp.TestMessage apply(AkkaAmqp.TestMessage request) {
                return AkkaAmqp.TestMessage.newBuilder().setMessage(request.getMessage()).build();
            }
        }, AkkaAmqp.TestMessage.class);

        RPC.RpcClient<AkkaAmqp.TestMessage, AkkaAmqp.TestMessage> protobufRpcClient =
                RPC.newProtobufRpcClient(connection, exchangeName, AkkaAmqp.TestMessage.class);

        scala.Option<AkkaAmqp.TestMessage> response =
                protobufRpcClient.call(AkkaAmqp.TestMessage.newBuilder().setMessage("akka-amqp rocks!").build());

        System.out.println("### >> Got response: " + response);
    }
}

class DummyActor extends UntypedActor {
    public void onReceive(Object message) throws Exception {
        // not used
    }
}

class ChannelCallbackActor extends UntypedActor {

    private final CountDownLatch channelCountdown;

    public ChannelCallbackActor(CountDownLatch channelCountdown) {
        this.channelCountdown = channelCountdown;
    }

    public void onReceive(Object message) throws Exception {
        if (Started.getInstance().getClass().isAssignableFrom(message.getClass())) {
            System.out.println("### >> Channel callback: Started");
            channelCountdown.countDown();
        } else if (Restarting.getInstance().getClass().isAssignableFrom(message.getClass())) {
        } else if (Stopped.getInstance().getClass().isAssignableFrom(message.getClass())) {
            System.out.println("### >> Channel callback: Stopped");
        } else throw new IllegalArgumentException("Unknown message: " + message);
    }
}

class ConnectionCallbackActor extends UntypedActor {

    public void onReceive(Object message) throws Exception {
        if (Connected.getInstance().getClass().isAssignableFrom(message.getClass())) {
            System.out.println("### >> Connection callback: Connected!");
        } else if (Reconnecting.getInstance().getClass().isAssignableFrom(message.getClass())) {
        } else if (Disconnected.getInstance().getClass().isAssignableFrom(message.getClass())) {
            System.out.println("### >> Connection callback: Disconnected!");
        } else throw new IllegalArgumentException("Unknown message: " + message);
    }
}

class DirectDeliveryHandlerActor extends UntypedActor {

    public void onReceive(Object message) throws Exception {
        if (Delivery.class.isAssignableFrom(message.getClass())) {
            Delivery delivery = (Delivery) message;
            System.out.println("### >> @george_bush received message from: " + new String(delivery.payload()));
        } else throw new IllegalArgumentException("Unknown message: " + message);
    }
}
