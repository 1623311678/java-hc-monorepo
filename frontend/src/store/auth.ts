"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { request } from "@/lib/api";

export type UserProfile = {
  id: number;
  username: string;
  phone?: string;
  email?: string;
  nickname?: string;
  avatar?: string;
  status: number;
};

type LoginPayload = {
  username: string;
  password: string;
};

type RegisterPayload = {
  username: string;
  password: string;
  phone?: string;
  email?: string;
  nickname?: string;
};

type LoginResponse = {
  token: string;
  user: UserProfile;
};

type AuthState = {
  token: string | null;
  user: UserProfile | null;
  loading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  fetchMe: () => Promise<void>;
  logout: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,
      loading: false,

      async login(payload) {
        set({ loading: true });
        try {
          const res = await request<LoginResponse>("/api/user/login", {
            method: "POST",
            body: payload,
          });
          set({ token: res.data.token, user: res.data.user, loading: false });
        } catch (err) {
          set({ loading: false });
          throw err;
        }
      },

      async register(payload) {
        set({ loading: true });
        try {
          await request<UserProfile>("/api/user/register", {
            method: "POST",
            body: payload,
          });
          set({ loading: false });
        } catch (err) {
          set({ loading: false });
          throw err;
        }
      },

      async fetchMe() {
        const token = get().token;
        if (!token) {
          set({ user: null });
          return;
        }

        set({ loading: true });
        try {
          const res = await request<UserProfile>("/api/user/me", { token });
          set({ user: res.data, loading: false });
        } catch {
          set({ token: null, user: null, loading: false });
        }
      },

      logout() {
        set({ token: null, user: null, loading: false });
      },
    }),
    {
      name: "hc-auth",
      partialize: (state) => ({ token: state.token, user: state.user }),
    }
  )
);
