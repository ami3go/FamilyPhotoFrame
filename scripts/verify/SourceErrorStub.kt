// Mirror of SourceError from PhotoSource.kt, so SynologyApi can be verified in isolation.
enum class SourceError { AuthFailed, HostUnreachable, ShareNotFound, PermissionDenied,
  Timeout, ProtocolError, FileGone, CorruptImage, Cancelled, PermissionRevoked,
  FolderMissing, FolderBlocked, ProviderError, Unknown,
  TwoFactorRequired, CertUntrusted, SessionExpired, QuickConnectUnavailable }
