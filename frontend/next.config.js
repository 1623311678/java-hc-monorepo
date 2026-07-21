/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  images: {
    domains: ['localhost', 'cdn.example.com'],
  },
  async rewrites() {
    // 本地联调模式：直连 user 服务（7101），跳过 gateway
    //   /api/user/login → http://localhost:7101/user/login  （去掉 /api 前缀）
    // 生产/全链路模式：NEXT_PUBLIC_API_BASE=http://localhost:7100 通过 gateway 转发
    //   /api/user/login → http://localhost:7100/api/user/login （gateway StripPrefix 处理）
    const base = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:7101';
    const useGateway = base.includes('7100');
    return [
      {
        source: '/api/:path*',
        // gateway 模式保留 /api 前缀，直连模式去掉 /api 前缀
        destination: useGateway
          ? `${base}/api/:path*`
          : `${base}/:path*`,
      },
    ];
  },
};
module.exports = nextConfig;
