// Mirrors backend's TotpSetupResponseDto / TotpStatusResponseDto.
export interface TotpSetup {
  secret: string;
  otpAuthUri: string;
}

/** How the second factor is delivered. */
export type TwoFactorMethod = 'TOTP' | 'EMAIL';

export interface TotpStatus {
  enabled: boolean;
  /** The method in force. Accounts enrolled before methods existed read TOTP. */
  method: TwoFactorMethod;
  /**
   * A method awaiting confirmation, if a switch is part-way through — so the
   * page can resume it rather than stranding the user between two methods.
   */
  pendingMethod: TwoFactorMethod | null;
}

/**
 * What the client needs to finish a method switch.
 *
 * <p>The shape differs by target: moving to the authenticator app returns a
 * secret and QR to scan, while moving to email returns only whether the code
 * actually went out.
 */
export interface TwoFactorMethodChange {
  pendingMethod: TwoFactorMethod | null;
  secret: string | null;
  otpAuthUri: string | null;
  /** False when the email could not be sent, so the UI can say so honestly. */
  codeDelivered: boolean;
  message: string | null;
}
