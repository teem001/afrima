export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details: string[] = [],
  ) {
    super(message);
  }

  static badRequest(code: string, message: string, details: string[] = []) {
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
  static conflict(code: string, message: string) {
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
