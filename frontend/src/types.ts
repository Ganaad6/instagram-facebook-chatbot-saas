// Shapes of the backend's JSON responses (see API.md). Timestamps are the server's local
// time without a zone (the shop's time zone), e.g. "2026-09-29T14:03:12".

export type Role = 'OWNER' | 'STAFF';

export interface StaffUser {
  id: number;
  email: string;
  name: string | null;
  role: Role;
  active: boolean;
  invitePending: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

/** A dashboard login of any shop, as the platform admin sees it. */
export interface AdminUser extends StaffUser {
  locked: boolean;
  lockedUntil: string | null;
  businessId: number;
  businessName: string;
  businessStatus: 'ACTIVE' | 'INACTIVE';
}

export interface Business {
  id: number;
  name: string;
  email: string;
  instagramAccountId: string | null;
  facebookPageId: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  metaConnected: boolean;
  qpayConnected: boolean;
  qpayUsername: string | null;
  notificationWebhookUrl: string | null;
  /** The bot's greeting on the first menu; null = the built-in one. */
  welcomeMessage: string | null;
  /** Delivery terms in the order confirmation; null = none. */
  deliveryNote: string | null;
  createdAt: string;
}

export interface Me {
  user: StaffUser;
  business: Business;
}

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'DELIVERED' | 'CANCELLED';
export type PaymentStatus = 'NOT_REQUESTED' | 'PENDING' | 'PAID';

export interface Order {
  id: number;
  customerId: number;
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  totalAmount: number;
  customerName: string | null;
  phone: string | null;
  address: string | null;
  status: OrderStatus;
  platform: 'FACEBOOK' | 'INSTAGRAM';
  notes: string | null;
  paymentStatus: PaymentStatus;
  paymentUrl: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface Category {
  id: number;
  name: string;
  sortOrder: number;
  isActive: boolean;
}

export interface Product {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  price: number;
  description: string | null;
  isActive: boolean;
  imageUrl: string | null;
}

export type SenderType = 'CUSTOMER' | 'BOT' | 'AGENT';

export interface ChatSummary {
  customerId: number;
  displayName: string | null;
  platform: 'FACEBOOK' | 'INSTAGRAM';
  lastMessage: string | null;
  lastMessageSender: SenderType | null;
  lastMessageAt: string | null;
  lastInteractionAt: string | null;
  botPausedUntil: string | null;
  handoffRequestedAt: string | null;
}

export interface Message {
  id: number;
  direction: 'INBOUND' | 'OUTBOUND';
  senderType: SenderType;
  content: string;
  sentAt: string;
}

export interface AnalyticsSummary {
  totalOrders: number;
  pendingOrders: number;
  todayOrders: number;
  totalRevenue: number;
  paidRevenue: number;
  awaitingPaymentOrders: number;
  topProducts: { productName: string; orderCount: number; totalQuantity: number }[];
}

export interface DailyCount {
  day: string;
  count: number;
}

export interface IssuedLink {
  url: string;
  expiresAt: string;
}
