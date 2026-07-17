/** Typed fetch wrapper for /api/v1 — session cookie, CSRF header, standard error envelope. */

const BASE_URL = "/api/v1";

/** Standard backend error envelope. */
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  fieldErrors: { field: string; message: string }[];
  requestId: string;
}

export class ApiRequestError extends Error {
  readonly error: ApiError;

  constructor(error: ApiError) {
    super(error.message);
    this.name = "ApiRequestError";
    this.error = error;
  }
}

function csrfToken(): string | undefined {
  return document.cookie
    .split("; ")
    .find((c) => c.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
}

export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const method = options.method ?? "GET";
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body != null) headers.set("Content-Type", "application/json");
  if (method !== "GET" && method !== "HEAD") {
    const token = csrfToken();
    if (token) headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: "include",
  });

  if (!response.ok) {
    throw new ApiRequestError((await response.json()) as ApiError);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
