// Mirrors backend's TotpSetupResponseDto / TotpStatusResponseDto.
export interface TotpSetup {
  secret: string;
  otpAuthUri: string;
}

export interface TotpStatus {
  enabled: boolean;
}
