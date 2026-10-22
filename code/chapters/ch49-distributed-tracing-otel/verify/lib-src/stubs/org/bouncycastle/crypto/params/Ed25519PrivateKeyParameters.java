package org.bouncycastle.crypto.params;
/** Compile-only stand-in: jnats uses Bouncy Castle only for NKey (Ed25519) authentication, which the lab doesn't use. */
public class Ed25519PrivateKeyParameters { public Ed25519PrivateKeyParameters(byte[] b) { throw new UnsupportedOperationException(); }
  public Ed25519PublicKeyParameters generatePublicKey() { throw new UnsupportedOperationException(); } public byte[] getEncoded() { return null; } }
