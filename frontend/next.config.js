/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  images: {
    domains: ['localhost', 'cdn.example.com'],
  },
};
module.exports = nextConfig;
