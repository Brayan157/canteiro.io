package com.renovar.canteiro.io.platform.support.api;

import com.renovar.canteiro.io.platform.support.api.request.CreatePlatformSupportUserRequest;
import com.renovar.canteiro.io.platform.support.api.response.PlatformSupportUserResponse;
import com.renovar.canteiro.io.platform.support.application.CreatePlatformSupportUserCommand;
import com.renovar.canteiro.io.platform.support.application.PlatformSupportUserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform/support-users")
@Tag(name = "Platform support users")
public class PlatformSupportUserController {

    private final PlatformSupportUserManagementService platformSupportUserManagementService;
    private final PlatformSupportApiMapper platformSupportApiMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a platform support user")
    public PlatformSupportUserResponse createSupportUser(@Valid @RequestBody CreatePlatformSupportUserRequest request) {
        return platformSupportApiMapper.toResponse(platformSupportUserManagementService.createSupportUser(
                new CreatePlatformSupportUserCommand(request.email())
        ));
    }
}
