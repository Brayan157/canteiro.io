package com.renovar.canteiro.io.employees.api;

import com.renovar.canteiro.io.employees.api.request.CreateEmployeeRequest;
import com.renovar.canteiro.io.employees.api.request.UpdateEmployeeRequest;
import com.renovar.canteiro.io.employees.api.request.InviteEmployeeAccessRequest;
import com.renovar.canteiro.io.employees.api.request.EmployeeAddressRequest;
import com.renovar.canteiro.io.employees.api.request.EmployeePaymentAccountRequest;
import com.renovar.canteiro.io.employees.api.response.EmployeeAccessInvitationResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeeAddressResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeePaymentAccountResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeePaymentDetailsResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeeChangeResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeeResponse;
import com.renovar.canteiro.io.employees.application.CreateEmployeeCommand;
import com.renovar.canteiro.io.employees.application.EmployeeAccessInvitationService;
import com.renovar.canteiro.io.employees.application.EmployeeManagementService;
import com.renovar.canteiro.io.employees.application.EmployeePrivateDetailsService;
import com.renovar.canteiro.io.employees.application.EmployeePhotoService;
import com.renovar.canteiro.io.employees.application.EmployeeFilter;
import com.renovar.canteiro.io.employees.application.InviteEmployeeAccessCommand;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/employees")
@Tag(name = "Employees")
@RequiredArgsConstructor
public class EmployeeController {

    private static final Set<String> SORT_FIELDS = Set.of("fullName", "jobTitle", "department", "employmentType",
            "compensationAmount", "compensationFrequency", "currentlyEmployed", "active", "createdAt", "updatedAt");
    private static final Set<String> EMPLOYEE_PHOTO_TYPES = Set.of("image/png", "image/jpeg");
    private static final long MAX_EMPLOYEE_PHOTO_SIZE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_EMPLOYEE_PHOTO_DIMENSION = 2048;

    private final EmployeeManagementService employeeManagementService;
    private final EmployeeAccessInvitationService employeeAccessInvitationService;
    private final EmployeePrivateDetailsService employeePrivateDetailsService;
    private final EmployeePhotoService employeePhotoService;

    @PostMapping
    @Operation(summary = "Registers an operational employee without creating system access")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Employee registered", content = @Content(schema = @Schema(implementation = EmployeeChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Employee creation submitted for approval", content = @Content(schema = @Schema(implementation = EmployeeChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<EmployeeChangeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        var result = employeeManagementService.create(new CreateEmployeeCommand(
                request.fullName(), request.preferredName(), request.jobTitle(), request.department(), request.phone(),
                request.registrationNumber(), request.documentType(), request.documentNumber(), request.pis(), request.birthDate(),
                request.employmentType(), request.employmentStartDate(), request.employmentEndDate(), request.currentlyEmployed(),
                request.gender(), request.ethnicity(), request.compensationAmount(), request.compensationFrequency(), request.justification()
        ));
        EmployeeResponse employee = result.employee() == null ? null : response(result.employee());
        return ResponseEntity.status(employee == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new EmployeeChangeResponse(employee,
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Finds an employee in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee found", content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public EmployeeResponse find(@PathVariable UUID employeeId) {
        return response(employeeManagementService.find(employeeId));
    }

    @GetMapping
    @Operation(summary = "Lists employees in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated employee list", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<EmployeeResponse> findAll(@RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) List<String> sort,
                                                  @RequestParam(required = false) String name,
                                                  @RequestParam(required = false) String document,
                                                  @RequestParam(required = false) String jobTitle,
                                                  @RequestParam(required = false) String department,
                                                  @RequestParam(required = false) EmployeeEmploymentType employmentType,
                                                  @RequestParam(required = false) EmployeeCompensationFrequency compensationFrequency,
                                                  @RequestParam(required = false) BigDecimal compensationMin,
                                                  @RequestParam(required = false) BigDecimal compensationMax,
                                                  @RequestParam(required = false) Boolean currentlyEmployed,
                                                  @RequestParam(required = false) Boolean active) {
        return PageResponse.from(employeeManagementService.findAll(
                new EmployeeFilter(name, document, jobTitle, department, employmentType, compensationFrequency,
                        compensationMin, compensationMax, currentlyEmployed, active),
                new PageQuery(page, size, sort).toPageable(SORT_FIELDS)), this::response);
    }

    @PostMapping("/{employeeId}/access-invitations")
    @Operation(summary = "Creates system access for an employee and sends an activation email")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Employee access invitation created", content = @Content(schema = @Schema(implementation = EmployeeAccessInvitationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<EmployeeAccessInvitationResponse> inviteAccess(@PathVariable UUID employeeId,
                                                                           @Valid @RequestBody InviteEmployeeAccessRequest request) {
        var result = employeeAccessInvitationService.invite(new InviteEmployeeAccessCommand(employeeId, request.email()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new EmployeeAccessInvitationResponse(
                result.employeeId(), result.userId(), result.email()
        ));
    }

    @PutMapping("/{employeeId}")
    @Operation(summary = "Updates the complete operational employee profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee updated", content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public EmployeeResponse update(@PathVariable UUID employeeId, @Valid @RequestBody UpdateEmployeeRequest request) {
        Employee employee = employeeManagementService.update(employeeId, new CreateEmployeeCommand(request.fullName(), request.preferredName(),
                request.jobTitle(), request.department(), request.phone(), null, request.documentType(), request.documentNumber(), request.pis(),
                request.birthDate(), request.employmentType(), request.employmentStartDate(), request.employmentEndDate(), request.currentlyEmployed(),
                request.gender(), request.ethnicity(), request.compensationAmount(), request.compensationFrequency(), request.justification()));
        return response(employee);
    }

    @PostMapping(path = "/{employeeId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploads an optional employee photo for internal visual identification only; no facial recognition or biometric processing is performed")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identification photo stored"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public EmployeePhotoUploadResponse uploadPhoto(@PathVariable UUID employeeId, @RequestPart("file") MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        if (file.isEmpty() || file.getSize() > MAX_EMPLOYEE_PHOTO_SIZE_BYTES || !EMPLOYEE_PHOTO_TYPES.contains(file.getContentType())
                || !hasValidPhoto(content, file.getContentType())) {
            throw new IllegalArgumentException("Employee photo must be a valid PNG or JPEG image up to 2 MiB and 2048 x 2048 pixels");
        }
        return new EmployeePhotoUploadResponse(employeePhotoService.upload(employeeId, content, file.getContentType()));
    }

    @GetMapping("/{employeeId}/photo")
    @Operation(summary = "Downloads the employee identification photo from the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee identification photo", content = @Content(mediaType = "image/png")),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<byte[]> downloadPhoto(@PathVariable UUID employeeId) {
        EmployeePhotoService.EmployeePhotoContent photo = employeePhotoService.load(employeeId);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(photo.contentType())).body(photo.content());
    }

    @DeleteMapping("/{employeeId}/photo")
    @Operation(summary = "Removes an employee identification photo and its private storage object")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Identification photo removed"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<Void> removePhoto(@PathVariable UUID employeeId) {
        employeePhotoService.remove(employeeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{employeeId}/addresses")
    @Operation(summary = "Registers an employee home or correspondence address")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Address registered", content = @Content(schema = @Schema(implementation = EmployeeAddressResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<EmployeeAddressResponse> addAddress(@PathVariable UUID employeeId,
                                                                @Valid @RequestBody EmployeeAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeePrivateDetailsService.addAddress(employeeId, request));
    }

    @GetMapping("/{employeeId}/addresses")
    @Operation(summary = "Lists employee addresses in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee addresses", content = @Content(schema = @Schema(implementation = EmployeeAddressResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public List<EmployeeAddressResponse> addresses(@PathVariable UUID employeeId) {
        return employeePrivateDetailsService.addresses(employeeId);
    }

    @PutMapping("/{employeeId}/addresses/{addressId}")
    @Operation(summary = "Updates an active employee address and can define it as primary")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address updated", content = @Content(schema = @Schema(implementation = EmployeeAddressResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public EmployeeAddressResponse updateAddress(@PathVariable UUID employeeId, @PathVariable UUID addressId,
                                                 @Valid @RequestBody EmployeeAddressRequest request) {
        return employeePrivateDetailsService.updateAddress(employeeId, addressId, request);
    }

    @PutMapping("/{employeeId}/addresses/{addressId}/primary")
    @Operation(summary = "Defines the active primary address for an employee")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Primary address updated", content = @Content(schema = @Schema(implementation = EmployeeAddressResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public EmployeeAddressResponse setPrimaryAddress(@PathVariable UUID employeeId, @PathVariable UUID addressId) {
        return employeePrivateDetailsService.setPrimaryAddress(employeeId, addressId);
    }

    @DeleteMapping("/{employeeId}/addresses/{addressId}")
    @Operation(summary = "Deactivates an employee address without deleting its history")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Address deactivated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<Void> deactivateAddress(@PathVariable UUID employeeId, @PathVariable UUID addressId) {
        employeePrivateDetailsService.deactivateAddress(employeeId, addressId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{employeeId}/payment-accounts")
    @Operation(summary = "Registers a masked and encrypted employee bank account or Pix key without initiating a payment")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment reference registered", content = @Content(schema = @Schema(implementation = EmployeePaymentAccountResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<EmployeePaymentAccountResponse> addPaymentAccount(@PathVariable UUID employeeId,
                                                                              @Valid @RequestBody EmployeePaymentAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeePrivateDetailsService.addPaymentAccount(employeeId, request));
    }

    @GetMapping("/{employeeId}/payment-accounts")
    @Operation(summary = "Lists masked employee payment account references without initiating a payment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Masked employee payment references", content = @Content(schema = @Schema(implementation = EmployeePaymentAccountResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public List<EmployeePaymentAccountResponse> paymentAccounts(@PathVariable UUID employeeId) {
        return employeePrivateDetailsService.paymentAccounts(employeeId);
    }

    @PostMapping("/{employeeId}/payment-accounts/{accountId}/payment-details/reveal")
    @Operation(summary = "Explicitly reveals complete employee payment data for a manual payment and records the consultation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Complete payment data revealed for this interaction", content = @Content(schema = @Schema(implementation = EmployeePaymentDetailsResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public EmployeePaymentDetailsResponse revealPaymentDetails(@PathVariable UUID employeeId, @PathVariable UUID accountId) {
        return employeePrivateDetailsService.revealPaymentDetails(employeeId, accountId);
    }

    @PutMapping("/{employeeId}/payment-accounts/{accountId}")
    @Operation(summary = "Updates employee bank or Pix data without returning its complete value")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment reference updated", content = @Content(schema = @Schema(implementation = EmployeePaymentAccountResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public EmployeePaymentAccountResponse updatePaymentAccount(@PathVariable UUID employeeId, @PathVariable UUID accountId,
                                                                @Valid @RequestBody EmployeePaymentAccountRequest request) {
        return employeePrivateDetailsService.updatePaymentAccount(employeeId, accountId, request);
    }

    @DeleteMapping("/{employeeId}/payment-accounts/{accountId}")
    @Operation(summary = "Deactivates an employee payment reference without deleting its history")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Payment reference deactivated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<Void> deactivatePaymentAccount(@PathVariable UUID employeeId, @PathVariable UUID accountId) {
        employeePrivateDetailsService.deactivatePaymentAccount(employeeId, accountId);
        return ResponseEntity.noContent().build();
    }

    private EmployeeResponse response(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getFullName(), employee.getPreferredName(), employee.getJobTitle(),
                employee.getDepartment(), employee.getPhone(), employee.getRegistrationNumber(), employee.getDocumentType(),
                employee.getDocumentNumber(), employee.getPisMasked(), employee.getBirthDate(), employee.getEmploymentType(),
                employee.getEmploymentStartDate(), employee.getEmploymentEndDate(),
                employee.isCurrentlyEmployed(), employee.getGender(), employee.getEthnicity(), employee.getCompensationFrequency(),
                employee.getCompensationAmount() == null ? null : employee.getCompensationAmount().toPlainString(),
                employee.isActive(), employee.getUserId(), employee.getPhotoStorageKey() == null ? null
                : "/api/v1/company/employees/" + employee.getId() + "/photo");
    }

    private boolean hasValidPhoto(byte[] content, String contentType) throws IOException {
        if (!hasExpectedSignature(content, contentType)) return false;
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
        return decoded != null && decoded.getWidth() > 0 && decoded.getHeight() > 0
                && decoded.getWidth() <= MAX_EMPLOYEE_PHOTO_DIMENSION && decoded.getHeight() <= MAX_EMPLOYEE_PHOTO_DIMENSION;
    }

    private boolean hasExpectedSignature(byte[] content, String contentType) {
        if (content.length < 12) return false;
        return switch (contentType) {
            case "image/png" -> content[0] == (byte) 0x89 && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47;
            case "image/jpeg" -> content[0] == (byte) 0xff && content[1] == (byte) 0xd8 && content[2] == (byte) 0xff;
            default -> false;
        };
    }

    public record EmployeePhotoUploadResponse(String photoUrl) {
    }
}
