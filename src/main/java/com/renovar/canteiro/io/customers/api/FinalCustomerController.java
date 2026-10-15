package com.renovar.canteiro.io.customers.api;

import com.renovar.canteiro.io.customers.api.request.CreateFinalCustomerRequest;
import com.renovar.canteiro.io.customers.api.request.FinalCustomerAddressRequest;
import com.renovar.canteiro.io.customers.api.request.FinalCustomerContactRequest;
import com.renovar.canteiro.io.customers.api.request.UpdateFinalCustomerRequest;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerAddressChangeResponse;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerAddressResponse;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerChangeResponse;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerChangeRequestResponse;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerContactChangeResponse;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerContactResponse;
import com.renovar.canteiro.io.customers.api.response.FinalCustomerResponse;
import com.renovar.canteiro.io.customers.application.CreateFinalCustomerCommand;
import com.renovar.canteiro.io.customers.application.FinalCustomerAddressCommand;
import com.renovar.canteiro.io.customers.application.FinalCustomerContactCommand;
import com.renovar.canteiro.io.customers.application.FinalCustomerManagementService;
import com.renovar.canteiro.io.customers.application.FinalCustomerFilter;
import com.renovar.canteiro.io.customers.application.UpdateFinalCustomerCommand;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/company/customers") @RequiredArgsConstructor
@Tag(name = "Final customers")
public class FinalCustomerController {
    private final FinalCustomerManagementService service;
    @PostMapping
    @Operation(summary = "Creates a final customer in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Final customer created", content = @Content(schema = @Schema(implementation = FinalCustomerChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Final customer creation submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerChangeResponse> create(@Valid @RequestBody CreateFinalCustomerRequest request) {
        var result = service.create(new CreateFinalCustomerCommand(request.customerType(), request.name(), request.document(),
                request.contacts() == null ? null : request.contacts().stream().map(c -> new CreateFinalCustomerCommand.Contact(c.name(), c.email(), c.phone(), c.primaryContact())).toList(),
                request.addresses() == null ? null : request.addresses().stream().map(a -> new CreateFinalCustomerCommand.Address(a.label(), a.postalCode(), a.street(), a.number(), a.complement(), a.district(), a.city(), a.state(), a.country(), a.primaryAddress())).toList(), request.justification()));
        FinalCustomerResponse customer = result.customer() == null ? null : response(result.customer());
        return ResponseEntity.status(result.customer() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED).body(new FinalCustomerChangeResponse(customer, result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }
    @GetMapping("/{customerId}")
    @Operation(summary = "Finds a final customer in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Final customer found", content = @Content(schema = @Schema(implementation = FinalCustomerResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public FinalCustomerResponse find(@PathVariable UUID customerId) { return response(service.find(customerId)); }

    @PutMapping("/{customerId}")
    @Operation(summary = "Updates a final customer in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Final customer updated", content = @Content(schema = @Schema(implementation = FinalCustomerChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Final customer update submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerChangeResponse> update(@PathVariable UUID customerId,
                                                               @Valid @RequestBody UpdateFinalCustomerRequest request) {
        var result = service.update(customerId, new UpdateFinalCustomerCommand(
                request.customerType(), request.name(), request.document(), request.justification()));
        FinalCustomerResponse customer = result.customer() == null ? null : response(result.customer());
        return ResponseEntity.status(customer == null ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new FinalCustomerChangeResponse(customer,
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @GetMapping(value = "/{customerId}/contacts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists active final customer contacts in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contacts found", content = @Content(array = @ArraySchema(schema = @Schema(implementation = FinalCustomerContactResponse.class)))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public List<FinalCustomerContactResponse> contacts(@PathVariable UUID customerId) {
        return service.contacts(customerId).stream().map(this::contactResponse).toList();
    }

    @PostMapping("/{customerId}/contacts")
    @Operation(summary = "Creates a final customer contact directly or submits it for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contact created", content = @Content(schema = @Schema(implementation = FinalCustomerContactChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contact creation submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerContactChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerContactChangeResponse> createContact(@PathVariable UUID customerId,
                                                                              @Valid @RequestBody FinalCustomerContactRequest request) {
        var result = service.createContact(customerId, contactCommand(request));
        return ResponseEntity.status(result.contact() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new FinalCustomerContactChangeResponse(result.contact() == null ? null : contactResponse(result.contact()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PutMapping("/{customerId}/contacts/{contactId}")
    @Operation(summary = "Updates a final customer contact directly or submits it for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contact updated", content = @Content(schema = @Schema(implementation = FinalCustomerContactChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contact update submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerContactChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerContactChangeResponse> updateContact(@PathVariable UUID customerId,
                                                                              @PathVariable UUID contactId,
                                                                              @Valid @RequestBody FinalCustomerContactRequest request) {
        var result = service.updateContact(customerId, contactId, contactCommand(request));
        return ResponseEntity.status(result.contact() == null ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new FinalCustomerContactChangeResponse(result.contact() == null ? null : contactResponse(result.contact()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PutMapping("/{customerId}/contacts/{contactId}/primary")
    @Operation(summary = "Defines the active primary contact directly or submits the change for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Primary contact updated", content = @Content(schema = @Schema(implementation = FinalCustomerContactChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Primary contact change submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerContactChangeResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerContactChangeResponse> setPrimaryContact(@PathVariable UUID customerId,
                                                                                  @PathVariable UUID contactId) {
        var result = service.setPrimaryContact(customerId, contactId);
        return ResponseEntity.status(result.contact() == null ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new FinalCustomerContactChangeResponse(result.contact() == null ? null : contactResponse(result.contact()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @DeleteMapping("/{customerId}/contacts/{contactId}")
    @Operation(summary = "Logically deactivates a final customer contact directly or submits the cancellation for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contact deactivated without physical deletion"),
            @ApiResponse(responseCode = "202", description = "Contact deactivation submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerChangeRequestResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<?> deactivateContact(@PathVariable UUID customerId, @PathVariable UUID contactId,
                                                @RequestParam(required = false) String justification) {
        var result = service.deactivateContact(customerId, contactId, justification);
        if (result.changeRequest() == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.accepted().body(new FinalCustomerChangeRequestResponse(result.changeRequest().getId(), result.mode()));
    }

    @GetMapping(value = "/{customerId}/addresses", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists active final customer addresses in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Addresses found", content = @Content(array = @ArraySchema(schema = @Schema(implementation = FinalCustomerAddressResponse.class)))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public List<FinalCustomerAddressResponse> addresses(@PathVariable UUID customerId) {
        return service.addresses(customerId).stream().map(this::addressResponse).toList();
    }

    @PostMapping("/{customerId}/addresses")
    @Operation(summary = "Creates a final customer address directly or submits it for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Address created", content = @Content(schema = @Schema(implementation = FinalCustomerAddressChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Address creation submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerAddressChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerAddressChangeResponse> createAddress(@PathVariable UUID customerId,
                                                                              @Valid @RequestBody FinalCustomerAddressRequest request) {
        var result = service.createAddress(customerId, addressCommand(request));
        return ResponseEntity.status(result.address() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new FinalCustomerAddressChangeResponse(result.address() == null ? null : addressResponse(result.address()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PutMapping("/{customerId}/addresses/{addressId}")
    @Operation(summary = "Updates a final customer address directly or submits it for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address updated", content = @Content(schema = @Schema(implementation = FinalCustomerAddressChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Address update submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerAddressChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerAddressChangeResponse> updateAddress(@PathVariable UUID customerId,
                                                                              @PathVariable UUID addressId,
                                                                              @Valid @RequestBody FinalCustomerAddressRequest request) {
        var result = service.updateAddress(customerId, addressId, addressCommand(request));
        return ResponseEntity.status(result.address() == null ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new FinalCustomerAddressChangeResponse(result.address() == null ? null : addressResponse(result.address()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PutMapping("/{customerId}/addresses/{addressId}/primary")
    @Operation(summary = "Defines the active primary address directly or submits the change for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Primary address updated", content = @Content(schema = @Schema(implementation = FinalCustomerAddressChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Primary address change submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerAddressChangeResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinalCustomerAddressChangeResponse> setPrimaryAddress(@PathVariable UUID customerId,
                                                                                  @PathVariable UUID addressId) {
        var result = service.setPrimaryAddress(customerId, addressId);
        return ResponseEntity.status(result.address() == null ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new FinalCustomerAddressChangeResponse(result.address() == null ? null : addressResponse(result.address()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @DeleteMapping("/{customerId}/addresses/{addressId}")
    @Operation(summary = "Logically deactivates a final customer address directly or submits the cancellation for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Address deactivated without physical deletion"),
            @ApiResponse(responseCode = "202", description = "Address deactivation submitted for approval", content = @Content(schema = @Schema(implementation = FinalCustomerChangeRequestResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<?> deactivateAddress(@PathVariable UUID customerId, @PathVariable UUID addressId,
                                                @RequestParam(required = false) String justification) {
        var result = service.deactivateAddress(customerId, addressId, justification);
        if (result.changeRequest() == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.accepted().body(new FinalCustomerChangeRequestResponse(result.changeRequest().getId(), result.mode()));
    }

    @GetMapping
    @Operation(summary = "Lists final customers in the authenticated company with safe name and document filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated final customer list", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<FinalCustomerResponse> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String document,
            @RequestParam(required = false) FinalCustomerType customerType,
            @RequestParam(required = false) Boolean active
    ) {
        return PageResponse.from(service.findAll(new FinalCustomerFilter(name, document, customerType, active),
                        new PageQuery(page, size, sort).toPageable(Set.of("name", "document", "customerType", "active", "createdAt", "updatedAt"))),
                this::response);
    }
    private FinalCustomerResponse response(com.renovar.canteiro.io.customers.domain.FinalCustomer c) { return new FinalCustomerResponse(c.getId(), c.getCustomerType(), c.getName(), c.getDocument(), c.isActive()); }
    private FinalCustomerContactCommand contactCommand(FinalCustomerContactRequest request) {
        return new FinalCustomerContactCommand(request.name(), request.email(), request.phone(), request.primaryContact(), request.justification());
    }
    private FinalCustomerAddressCommand addressCommand(FinalCustomerAddressRequest request) {
        return new FinalCustomerAddressCommand(request.label(), request.postalCode(), request.street(), request.number(),
                request.complement(), request.district(), request.city(), request.state(), request.country(), request.primaryAddress(),
                request.justification());
    }
    private FinalCustomerContactResponse contactResponse(com.renovar.canteiro.io.customers.domain.FinalCustomerContact contact) {
        return new FinalCustomerContactResponse(contact.getId(), contact.getName(), contact.getEmail(), contact.getPhone(),
                contact.isPrimaryContact(), contact.isActive());
    }
    private FinalCustomerAddressResponse addressResponse(com.renovar.canteiro.io.customers.domain.FinalCustomerAddress address) {
        return new FinalCustomerAddressResponse(address.getId(), address.getLabel(), address.getPostalCode(), address.getStreet(),
                address.getNumber(), address.getComplement(), address.getDistrict(), address.getCity(), address.getState(),
                address.getCountry(), address.isPrimaryAddress(), address.isActive());
    }
}
