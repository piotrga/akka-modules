package akka.camel;

import akka.japi.SideEffect;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static akka.camel.CamelContextManager.*;
import static akka.camel.CamelServiceManager.*;

/**
 * @author Martin Krasser
 */
public class ConsumerJavaTestBase {

    @BeforeClass
    public static void setUpBeforeClass() {
        startCamelService();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        stopCamelService();
        registry().shutdownAll();
    }

    @Test
    public void shouldHandleExceptionThrownByActorAndGenerateCustomResponse() {
        getMandatoryService().awaitEndpointActivation(1, new SideEffect() {
            public void apply() {
                actorOf(SampleErrorHandlingConsumer.class).start();
            }
        });
        String result = getMandatoryTemplate().requestBody("direct:error-handler-test-java", "hello", String.class);
        assertEquals("error: hello", result);
    }
}
