package com.cs6650.warehouseservice.consumer;

import com.cs6650.warehouseservice.service.WarehouseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class WarehouseConsumer {

  private final WarehouseService warehouseService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public WarehouseConsumer(WarehouseService warehouseService) {
    this.warehouseService = warehouseService;
  }

  @RabbitListener(queues = "checkoutQueue", ackMode = "MANUAL")
  public void receiveMessage(Message message, Channel channel) {
    try {
      String json = new String(message.getBody());
      JsonNode root = objectMapper.readTree(json);

      int orderId = root.get("order_id").asInt();
      JsonNode products = root.get("products");

      warehouseService.recordOrder(orderId, products);

      channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    } catch (Exception e) {
      System.err.println("Error processing message: " + e.getMessage());
      try {
        channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }
}
