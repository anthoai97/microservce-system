package anquach.dev.orderservice.service;

import anquach.dev.orderservice.dto.InventoryResponse;
import anquach.dev.orderservice.dto.OrderLineItemsDto;
import anquach.dev.orderservice.dto.OrderRequest;
import anquach.dev.orderservice.model.Order;
import anquach.dev.orderservice.model.OrderLineItems;
import anquach.dev.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        log.info(orderRequest.toString());

        List<OrderLineItems> orderLineItemsList = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDo)
                .toList();
        order.setOrderLineItemList(orderLineItemsList);

        List<String> skuCodes = orderLineItemsList.stream()
                .map(OrderLineItems::getSkuCode).toList();

        // Call Inventory Service, and place the order if product is in stock
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory", uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        if (inventoryResponseArray != null) {
            boolean allProductInStock = Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);
            if (inventoryResponseArray.length > 0 && allProductInStock) {
                orderRepository.save(order);
            } else {
                throw new IllegalArgumentException("Product is not in stock, pleas try again later");
            }
        } else {
            throw new IllegalArgumentException("Failed to connect Inventory Service");
        }

    }

    private OrderLineItems mapToDo(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
