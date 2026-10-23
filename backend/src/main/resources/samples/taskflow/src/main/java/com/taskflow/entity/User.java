package com.taskflow.entity;

/** 用户实体（演示数据）。 */
public class User {

    private Long id;
    private String username;
    /** 存储的是 BCrypt 哈希，不存明文 */
    private String passwordHash;
    private String nickname;
    private String email;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
