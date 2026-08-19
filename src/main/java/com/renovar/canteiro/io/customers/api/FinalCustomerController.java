package com.renovar.canteiro.io.customers.api;

import com.renovar.canteiro.io.customers.api.request.CreateFinalCustomerRequest;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerChangeResponse;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerResponse;
import com.renovar.canteiro.io.customers.application.CreateFinalCustomerCommand;
import com.renovar.canteiro.io.customers.application.FinalCustomerManagementService;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/company/customers") @RequiredArgsConstructor
public class FinalCustomerController {
    private final FinalCustomerManagementService service;
    @PostMapping
    public ResponseEntity<FinalCustomerChangeResponse> create(@Valid @RequestBody CreateFinalCustomerRequest request) {
        var result = service.create(new CreateFinalCustomerCommand(request.customerType(), request.name(), request.document(),
                request.contacts() == null ? null : request.contacts().stream().map(c -> new CreateFinalCustomerCommand.Contact(c.name(), c.email(), c.phone(), c.primaryContact())).toList(),
                request.addresses() == null ? null : request.addresses().stream().map(a -> new CreateFinalCustomerCommand.Address(a.label(), a.postalCode(), a.street(), a.number(), a.complement(), a.district(), a.city(), a.state(), a.country(), a.primaryAddress())).toList(), request.justification()));
        FinalCustomerResponse customer = result.customer() == null ? null : response(result.customer());
        return ResponseEntity.status(result.customer() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED).body(new FinalCustomerChangeResponse(customer, result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }
    @GetMapping("/{customerId}") public FinalCustomerResponse find(@PathVariable UUID customerId) { return response(service.find(customerId)); }
    @GetMapping
    public PageResponse<FinalCustomerResponse> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(service.findAll(new PageQuery(page, size, sort).toPageable(Set.of("name", "createdAt"))),
                this::response);
    }
    private FinalCustomerResponse response(com.renovar.canteiro.io.customers.domain.FinalCustomer c) { return new FinalCustomerResponse(c.getId(), c.getCustomerType(), c.getName(), c.getDocument(), c.isActive()); }
}
