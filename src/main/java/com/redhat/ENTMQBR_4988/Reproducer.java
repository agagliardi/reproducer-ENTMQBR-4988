package com.redhat.ENTMQBR_4988;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reproducer {

  private static final String TOPIC = "test";
  private static final String SUBSCRIPTION = "test_subscription";
  private static Logger log = LoggerFactory.getLogger(Reproducer.class);
  private static final CountDownLatch cdl = new CountDownLatch(1);

  public static void main(final String[] args) throws Exception {

    String brokerURL = "(tcp://localhost:61616,tcp://localhost:61617)";
    if (args.length > 0) {
      brokerURL = args[0];
    }
    brokerURL = System.getProperty("brokerURL", brokerURL);

    try (final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerURL)) {
      connectionFactory.setUseTopologyForLoadBalancing(false);
      // connectionFactory.setCallFailoverTimeout(1000);// fail fast
      // connectionFactory.setCallTimeout(1000);// fail fast
      connectionFactory.setReconnectAttempts(-1);
      connectionFactory.setInitialConnectAttempts(10);
      connectionFactory.setConfirmationWindowSize(1024 * 1024);
      connectionFactory.setConsumerWindowSize(0);

      try (final Connection consumerConnection = connectionFactory.createConnection();) {
        consumerConnection.start();
        final Session session = consumerConnection.createSession(Session.SESSION_TRANSACTED);
        final MessageConsumer messageConsumer = session
            .createSharedDurableConsumer(ActiveMQDestination.createTopic(TOPIC), SUBSCRIPTION);
        AtomicInteger counter = new AtomicInteger();

        messageConsumer.setMessageListener(message -> {
          try {
            try {
              log.info("#######  Message received");
              log.info("#######  Break the network then press ENTER to start rollback!");
              System.in.read();
              session.rollback();
              log.error("#######  Test failed - first rollback must fails");
              exit();
            } catch (JMSException jmsException) {
              log.info("#######  Rollback failed as expected, fix network then press ENTER to retry:{}",
                  jmsException.getMessage());
              System.in.read();
            }
            try {
              session.rollback();
            } catch (JMSException e) {
              log.error("#######  Rollback failed agan! give up:{}", e.getMessage());
              exit();
            }
            log.info("####### Setting echo listener");
            try {
              messageConsumer.setMessageListener(message1 -> {
                log.info("#######  Message received:{}", counter.incrementAndGet());
                try {
                  session.commit();
                } catch (JMSException e) {
                  log.error("#######  Commit failed :{}", e.getMessage());
                  exit();
                }
              });
            } catch (JMSException e) {
              log.error("#######  Unable to use the good listener {}", e.getMessage());
              exit();
            }
          } catch (Exception e) {
            log.error("Unpredicted Exception - test failed", e);
            exit();
          }
        });

        System.out.println("CTRL-C to exit");
        System.out.println("Start producing some message on " + brokerURL + " address " + TOPIC);
        cdl.await();

      }
    }
  }

  private static final void exit() {
    cdl.countDown();
  }

}
