"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useAuthStore } from "@/store/auth";

/**
 * 电商首页 — 商品列表 + 秒杀入口
 */
export default function HomePage() {
  const { token, user, fetchMe, logout } = useAuthStore();

  useEffect(() => {
    if (token && !user) {
      fetchMe();
    }
  }, [token, user, fetchMe]);

  // 后续用 react-query 从 /api/product/spu/page 拉取
  const products = [
    { id: 1, name: "iPhone 15 Pro", price: 7999, image: "/placeholder.jpg" },
    { id: 2, name: "MacBook Pro 14", price: 14999, image: "/placeholder.jpg" },
    { id: 3, name: "AirPods Pro 2", price: 1899, image: "/placeholder.jpg" },
    { id: 4, name: "iPad Air M2", price: 4799, image: "/placeholder.jpg" },
    { id: 5, name: "Apple Watch Ultra 2", price: 6499, image: "/placeholder.jpg" },
  ];

  return (
    <div className="min-h-screen">
      {/* ===== 顶部导航 ===== */}
      <header className="sticky top-0 z-50 bg-white shadow-sm">
        <nav className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between">
          <h1 className="text-xl font-bold text-indigo-600">HC 电商</h1>
          <div className="flex items-center gap-6">
            <Link href="/cart" className="text-sm hover:text-indigo-600">购物车</Link>
            <Link href="/orders" className="text-sm hover:text-indigo-600">我的订单</Link>
            {user ? (
              <div className="flex items-center gap-3">
                <span className="text-sm text-gray-700">你好，{user.nickname || user.username}</span>
                <button
                  onClick={logout}
                  className="text-sm bg-gray-100 text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-200"
                >
                  退出
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <Link href="/register" className="text-sm hover:text-indigo-600">注册</Link>
                <Link href="/login" className="text-sm bg-indigo-600 text-white px-4 py-1.5 rounded-lg">登录</Link>
              </div>
            )}
          </div>
        </nav>
      </header>

      {/* ===== 秒杀 Banner ===== */}
      <section className="mx-auto max-w-7xl px-4 py-4">
        <div className="bg-gradient-to-r from-red-600 to-orange-500 rounded-xl p-6 text-white">
          <h2 className="text-2xl font-bold">⚡ 限时秒杀</h2>
          <p className="mt-1 text-red-100 seckill-flash">距结束 02:35:18</p>
          <Link href="/seckill" className="mt-3 inline-block bg-white text-red-600 px-6 py-2 rounded-lg font-semibold hover:bg-red-50">
            立即抢购
          </Link>
        </div>
      </section>

      {/* ===== 商品网格 ===== */}
      <main className="mx-auto max-w-7xl px-4 py-6">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {products.map((p) => (
            <Link
              key={p.id}
              href={"/product/" + p.id}
              className="bg-white rounded-xl p-4 hover:shadow-lg transition-shadow"
            >
              <div className="aspect-square bg-gray-100 rounded-lg mb-3" />
              <h3 className="text-sm font-medium line-clamp-2">{p.name}</h3>
              <p className="mt-2 text-lg font-bold text-red-600">
                ¥{p.price.toLocaleString()}
              </p>
            </Link>
          ))}
        </div>
      </main>

      {/* ===== 底部 ===== */}
      <footer className="border-t mt-16 py-8 text-center text-sm text-gray-400">
        HC E-Commerce — Spring Cloud Alibaba 微服务电商
      </footer>
    </div>
  );
}
