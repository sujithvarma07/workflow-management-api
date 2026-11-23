package com.relic.backend.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;

/**
 * Topology:
 *
 *   workflow.events (topic exchange)
 *        |  routing key "task.completed"
 *        v
 *   workflow.events.task-completed (queue)  <-- @RabbitListener consumes here
 *
 * On a message that keeps failing, the listener container's retry advice
 * (below) gives it 3 local attempts with backoff, then hands it to a
 * RepublishMessageRecoverer, which republishes it to:
 *
 *   workflow.events.dlx (topic exchange)
 *        |  routing key "task.completed.dead"
 *        v
 *   workflow.events.task-completed.dlq (queue)  <-- parked for inspection/replay
 *
 * Replaying: DeadLetterReplayService reads messages off the DLQ and
 * republishes them to the original exchange/routing key, so a fixed bug or a
 * recovered downstream dependency can be retried without losing the message
 * (see docs/reliability-report.md for the operational procedure).
 */
@Configuration
public class RabbitMqConfig {

    public static final String EVENTS_EXCHANGE = "workflow.events";
    public static final String TASK_COMPLETED_QUEUE = "workflow.events.task-completed";
    public static final String TASK_COMPLETED_ROUTING_KEY = "task.completed";

    public static final String DEAD_LETTER_EXCHANGE = "workflow.events.dlx";
    public static final String TASK_COMPLETED_DLQ = "workflow.events.task-completed.dlq";
    public static final String TASK_COMPLETED_DEAD_ROUTING_KEY = "task.completed.dead";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE);
    }

    @Bean
    public Queue taskCompletedQueue() {
        return QueueBuilder.durable(TASK_COMPLETED_QUEUE).build();
    }

    @Bean
    public Binding taskCompletedBinding(Queue taskCompletedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(taskCompletedQueue).to(eventsExchange).with(TASK_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue taskCompletedDeadLetterQueue() {
        return QueueBuilder.durable(TASK_COMPLETED_DLQ).build();
    }

    @Bean
    public Binding taskCompletedDeadLetterBinding(Queue taskCompletedDeadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(taskCompletedDeadLetterQueue).to(deadLetterExchange).with(TASK_COMPLETED_DEAD_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public RetryOperationsInterceptor rabbitRetryInterceptor(RabbitTemplate rabbitTemplate) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, DEAD_LETTER_EXCHANGE, TASK_COMPLETED_DEAD_ROUTING_KEY))
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            RetryOperationsInterceptor rabbitRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAdviceChain(rabbitRetryInterceptor);
        return factory;
    }
}
