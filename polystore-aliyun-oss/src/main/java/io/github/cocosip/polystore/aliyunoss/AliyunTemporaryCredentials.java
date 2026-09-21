package io.github.cocosip.polystore.aliyunoss;

/**
 * One set of Aliyun STS temporary credentials, resolved through {@code AssumeRole} and used to sign
 * OSS requests instead of a long-lived access key pair.
 *
 * @param accessKeyId     temporary access key id
 * @param accessKeySecret temporary access key secret
 * @param securityToken   temporary security token
 */
record AliyunTemporaryCredentials(String accessKeyId, String accessKeySecret, String securityToken) {}
