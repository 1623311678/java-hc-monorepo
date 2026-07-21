// 本地开发时，API 请求走 Next.js rewrites 代理（/api/* → gateway）
// 这样前端 localhost:3000 → Next.js 代理 → 网关 localhost:7100，无跨域问题
// 生产环境可通过 NEXT_PUBLIC_API_BASE 环境变量覆盖
export const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "";

export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  token?: string;
};

export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const { method = "GET", body, token } = options;
  const resp = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    cache: "no-store",
  });

  const json = (await resp.json()) as ApiResponse<T>;
  if (!resp.ok) {
    throw new Error(json.message || `HTTP ${resp.status}`);
  }
  return json;
}
