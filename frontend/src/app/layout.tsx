import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "HC E-Commerce — 电商高并发",
  description: "Spring Cloud Alibaba 微服务电商项目",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="bg-gray-50 text-gray-900 antialiased">{children}</body>
    </html>
  );
}
