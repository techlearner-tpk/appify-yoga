import type { Metadata, Viewport } from 'next';
import './styles.css';
import { ServiceWorker } from '@/components/ServiceWorker';

export const metadata: Metadata = {title:'Appify Wellness',description:'Build a habit of showing up, one day at a time.',applicationName:'Appify Wellness',manifest:'/manifest.webmanifest'};
export const viewport: Viewport = {themeColor:'#f7f5ef',width:'device-width',initialScale:1};
export default function RootLayout({children}:{children:React.ReactNode}) {return <html lang="en"><body><ServiceWorker/>{children}</body></html>}
