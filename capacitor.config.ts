import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.streamtv.webview',
  appName: 'DiON streamTV',
  webDir: 'dist',
  server: {
    allowNavigation: ['*'],
  },
  android: {
    backgroundColor: '#090b10',
  },
};

export default config;
