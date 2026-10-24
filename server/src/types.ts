export type Role = "CONSUMER" | "MERCHANT";
export type OrderStatus = "PLACED" | "ACCEPTED" | "FULFILLED" | "CANCELLED";

export interface UserView {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  role: Role;
}
