import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // dev-only: forward API calls to the Spring Boot backend, no CORS needed
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
