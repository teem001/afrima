export class ApiError extends Error {
    status;
    code;
    details;
    constructor(status, code, message, details = []) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
    static badRequest(code, message, details = []) {
        return new ApiError(400, code, message, details);
    }
    static unauthorized(message = "Unauthorized") {
        return new ApiError(401, "UNAUTHORIZED", message);
    }
    static forbidden(message = "Forbidden") {
        return new ApiError(403, "FORBIDDEN", message);
    }
    static notFound(message = "Not found") {
        return new ApiError(404, "NOT_FOUND", message);
    }
    static conflict(code, message) {
        return new ApiError(409, code, message);
    }
    toJSON() {
        return {
            code: this.code,
            message: this.message,
            status: this.status,
            timestamp: new Date().toISOString(),
            details: this.details,
        };
    }
}
//# sourceMappingURL=errors.js.map