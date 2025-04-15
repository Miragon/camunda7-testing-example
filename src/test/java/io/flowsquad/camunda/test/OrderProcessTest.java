package io.flowsquad.camunda.test;

import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.extension.mockito.ProcessExpressions;
import org.camunda.bpm.scenario.ProcessScenario;
import org.camunda.bpm.scenario.Scenario;
import org.camunda.bpm.scenario.delegate.TaskDelegate;
import org.camunda.community.process_test_coverage.junit5.platform7.ProcessEngineCoverageExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.flowsquad.camunda.test.OrderprocessProcessApiV1.Elements.*;
import static io.flowsquad.camunda.test.OrderprocessProcessApiV1.PROCESS_ID;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;
import static org.mockito.Mockito.*;

@Deployment(resources = "order-process.bpmn")
@ExtendWith(ProcessEngineCoverageExtension.class)
public class OrderProcessTest {

    public static final String VAR_PRODUCTS_AVAILABLE = "productsAvailable";
    public static final String VAR_ORDER_DELIVERED = "orderDelivered";
    public static final String VAR_CUSTOMER = "customer";

    public static ProcessEngineCoverageExtension extension = ProcessEngineCoverageExtension
            // NOTE: Each model creates a PROCESS_ID which could be imported only once!
            // SUGGESTION: Could the bpmn-to-code plugin create ORDER_PROCESS_ID, DELIVERY_PROCESS_ID instead of only PROCESS_ID?
            .builder()
            .excludeProcessDefinitionKeys(io.flowsquad.camunda.test.DeliveryprocessProcessApiV1.PROCESS_ID)
            .assertClassCoverageAtLeast(0.9)
            .build();

    @Mock
    private ProcessScenario testOrderProcess;

    @Mock
    private MailingService mailingService;

    @BeforeEach
    public void defaultScenario() {
        MockitoAnnotations.initMocks(this);

        //Happy-Path
        when(testOrderProcess.waitsAtUserTask(Task_CheckAvailability))
                .thenReturn(task -> task.complete(withVariables(VAR_PRODUCTS_AVAILABLE, true)));

        when(testOrderProcess.waitsAtUserTask(Task_PrepareOrder))
                .thenReturn(TaskDelegate::complete);

        when(testOrderProcess.waitsAtUserTask(Task_DeliverOrder))
                .thenReturn(task -> task.complete(withVariables(VAR_ORDER_DELIVERED, true)));
    }

    @DisplayName("Send cancellation email")
    @Test
    public void shouldExecuteCancellationSent() {
        //Register implementation of SendCancellationDelegate (with private member mailingService), see Mocks
        Mocks.register("sendCancellationDelegate", new SendCancellationDelegate(mailingService));

        when(testOrderProcess.waitsAtUserTask(Task_CheckAvailability)).thenReturn(task -> task.complete(withVariables(VAR_PRODUCTS_AVAILABLE, false)));

        Scenario.run(testOrderProcess)
                .startByKey(PROCESS_ID, withVariables(VAR_CUSTOMER, "john"))
                .execute();

        verify(mailingService, (times(1))).sendMail(any());
        verifyNoMoreInteractions(mailingService);
        verify(testOrderProcess)
                .hasFinished(EndEvent_CancellationSent);
    }

    @DisplayName("Happy Path")
    @Test
    public void shouldExecuteHappyPath() {
        //Include partial process scenario
        //ProcessExpressions.registerCallActivityMock(io.flowsquad.camunda.test.DeliveryprocessProcessApiV1.PROCESS_ID)
        //        ..deploy(extension);

        Scenario.run(testOrderProcess)
                .startByKey(PROCESS_ID, withVariables(VAR_CUSTOMER, "john"))
                .execute();

        verify(testOrderProcess)
                .hasFinished(EndEvent_OrderFullfilled);
    }
}
