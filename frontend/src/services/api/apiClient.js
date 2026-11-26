/**
 * SearchForge Centralized API Client
 *
 * Provides a standardized HTTP transport for communication with the Spring Boot backend.
 * Handles timeouts, network failures, HTTP status validation, and JSON serialization.
 */

const DEFAULT_TIMEOUT_MS = 10000;

export class ApiError extends Error {
  constructor(message, { status = 0, details = null, isNetworkError = false, isTimeout = false } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
    this.isNetworkError = isNetworkError;
    this.isTimeout = isTimeout;
  }
}

class ApiClient {
  constructor() {
    const envUrl = typeof import.meta !== 'undefined' && import.meta.env
      ? import.meta.env.VITE_API_BASE_URL
      : (typeof process !== 'undefined' && process.env ? process.env.VITE_API_BASE_URL : null);
    this.baseUrl = (envUrl ?? 'http://localhost:8080').replace(/\/+$/, '');
  }

  /**
   * Performs an HTTP request with timeout, error handling, and JSON parsing.
   *
   * @param {string} endpoint - Relative path (e.g. '/api/v1/search')
   * @param {Object} options - Fetch options (method, headers, body, timeoutMs, etc.)
   * @returns {Promise<any>} Parsed response data
   */
  async request(endpoint, options = {}) {
    const {
      method = 'GET',
      headers = {},
      body,
      params,
      timeoutMs = DEFAULT_TIMEOUT_MS,
      ...customConfig
    } = options;

    // Construct full URL with query parameters if provided
    let url = `${this.baseUrl}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
    if (params) {
      const searchParams = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          searchParams.append(key, value);
        }
      });
      const queryString = searchParams.toString();
      if (queryString) {
        url += (url.includes('?') ? '&' : '?') + queryString;
      }
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    const config = {
      method,
      headers: {
        'Accept': 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      signal: controller.signal,
      ...customConfig,
    };

    if (body) {
      config.body = typeof body === 'string' ? body : JSON.stringify(body);
    }

    try {
      const response = await fetch(url, config);
      clearTimeout(timeoutId);

      // Parse JSON if response has content
      let data = null;
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        try {
          data = await response.json();
        } catch {
          data = null;
        }
      } else {
        const text = await response.text();
        data = text ? { raw: text } : null;
      }

      if (!response.ok) {
        const errorMessage = data?.message || data?.error || `HTTP ${response.status}: ${response.statusText}`;
        throw new ApiError(errorMessage, {
          status: response.status,
          details: data,
        });
      }

      return data;
    } catch (err) {
      clearTimeout(timeoutId);

      if (err instanceof ApiError) {
        throw err;
      }

      if (err.name === 'AbortError') {
        throw new ApiError(`Request timed out after ${timeoutMs}ms`, {
          isTimeout: true,
          status: 408,
        });
      }

      throw new ApiError(
        err.message || 'Unable to connect to SearchForge backend server. Please check your network or server status.',
        {
          isNetworkError: true,
          status: 0,
        }
      );
    }
  }

  get(endpoint, options = {}) {
    return this.request(endpoint, { ...options, method: 'GET' });
  }

  post(endpoint, body, options = {}) {
    return this.request(endpoint, { ...options, method: 'POST', body });
  }

  put(endpoint, body, options = {}) {
    return this.request(endpoint, { ...options, method: 'PUT', body });
  }

  delete(endpoint, options = {}) {
    return this.request(endpoint, { ...options, method: 'DELETE' });
  }
}

export const apiClient = new ApiClient();
export default apiClient;
