package org.mozilla.geckoview;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.mozilla.gecko.annotation.WrapForJNI;
import org.mozilla.gecko.util.GeckoBundle;

/**
 * Clean GMS-free replacement for GeckoView 115's WebAuthnTokenManager on Android 4.4 KitKat.
 * Stock WebAuthnTokenManager links directly against com.google.android.gms.fido.fido2.*,
 * causing Dalvik <clinit> NoClassDefFoundError -> native JNI MOZ_CRASH (SIGSEGV) whenever
 * accounts.google.com or modern login pages probe Passkey / WebAuthn availability.
 */
class WebAuthnTokenManager {

    public static class Exception extends RuntimeException {
        public Exception(final String error) {
            super(error);
        }
    }

    public static class MakeCredentialResponse {
        public final byte[] clientDataJson;
        public final byte[] keyHandle;
        public final byte[] attestationObject;

        @WrapForJNI
        public MakeCredentialResponse(
                final byte[] clientDataJson,
                final byte[] keyHandle,
                final byte[] attestationObject) {
            this.clientDataJson = clientDataJson;
            this.keyHandle = keyHandle;
            this.attestationObject = attestationObject;
        }
    }

    public static class GetAssertionResponse {
        public final byte[] clientDataJson;
        public final byte[] keyHandle;
        public final byte[] authData;
        public final byte[] signature;
        public final byte[] userHandle;

        @WrapForJNI
        public GetAssertionResponse(
                final byte[] clientDataJson,
                final byte[] keyHandle,
                final byte[] authData,
                final byte[] signature,
                final byte[] userHandle) {
            this.clientDataJson = clientDataJson;
            this.keyHandle = keyHandle;
            this.authData = authData;
            this.signature = signature;
            this.userHandle = userHandle;
        }
    }

    public static class WebAuthnPublicCredential {
        public final byte[] id;
        public final byte transports;

        public WebAuthnPublicCredential(final byte[] aId, final byte aTransports) {
            this.id = aId;
            this.transports = aTransports;
        }

        static ArrayList<WebAuthnPublicCredential> CombineBuffers(
                final Object[] idObjectList, final ByteBuffer transportList) {
            return new ArrayList<WebAuthnPublicCredential>();
        }
    }

    WebAuthnTokenManager() {}

    public static GeckoResult<MakeCredentialResponse> makeCredential(
            final GeckoBundle credentialBundle,
            final byte[] userId,
            final byte[] challenge,
            final WebAuthnPublicCredential[] excludeList,
            final GeckoBundle authenticatorSelection,
            final GeckoBundle extensions) {
        return GeckoResult.fromException(new Exception("NOT_SUPPORTED_ERR"));
    }

    @WrapForJNI
    private static GeckoResult<MakeCredentialResponse> webAuthnMakeCredential(
            final GeckoBundle credentialBundle,
            final ByteBuffer userId,
            final ByteBuffer challenge,
            final Object[] idList,
            final ByteBuffer transportList,
            final GeckoBundle authenticatorSelection,
            final GeckoBundle extensions) {
        return GeckoResult.fromException(new Exception("NOT_SUPPORTED_ERR"));
    }

    @WrapForJNI
    private static GeckoResult<GetAssertionResponse> webAuthnGetAssertion(
            final ByteBuffer challenge,
            final Object[] idList,
            final ByteBuffer transportList,
            final GeckoBundle allowCredentials,
            final GeckoBundle extensions) {
        return GeckoResult.fromException(new Exception("NOT_SUPPORTED_ERR"));
    }

    @WrapForJNI
    private static GeckoResult<Boolean> webAuthnIsUserVerifyingPlatformAuthenticatorAvailable() {
        return GeckoResult.fromValue(Boolean.FALSE);
    }
}
