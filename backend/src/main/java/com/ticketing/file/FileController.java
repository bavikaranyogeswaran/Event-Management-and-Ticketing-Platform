package com.ticketing.file;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.file.dto.DownloadUrlResponse;
import com.ticketing.file.dto.FileAssetResponse;
import com.ticketing.file.dto.UploadRequest;
import com.ticketing.file.dto.UploadRequestResponse;
import com.ticketing.shared.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/files")
class FileController {

    private final FileUploadService uploadService;
    private final FileDeleteService deleteService;
    private final FileService fileService;

    FileController(FileUploadService uploadService, FileDeleteService deleteService, FileService fileService) {
        this.uploadService = uploadService;
        this.deleteService = deleteService;
        this.fileService = fileService;
    }

    @PostMapping("/upload-requests")
    @ResponseStatus(HttpStatus.CREATED)
    UploadRequestResponse requestUpload(CurrentUser currentUser, @Valid @RequestBody UploadRequest request) {
        UploadTicket ticket = uploadService.requestUpload(currentUser.userId(),
                request.purpose(), request.mime(), request.sizeBytes());
        return UploadRequestResponse.of(ticket.fileId(), ticket.upload());
    }

    @PostMapping("/{fileId}/complete")
    FileAssetResponse complete(CurrentUser currentUser, @PathVariable UUID fileId) {
        CompletedUpload completed = uploadService.complete(currentUser.userId(), fileId);
        return FileAssetResponse.of(completed.asset(), completed.url());
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser currentUser, @PathVariable UUID fileId) {
        deleteService.delete(currentUser.userId(), fileId);
    }

    @GetMapping("/{fileId}/download-url")
    DownloadUrlResponse downloadUrl(CurrentUser currentUser, @PathVariable UUID fileId) {
        return fileService.signedExportUrl(fileId, currentUser.userId());
    }
}
