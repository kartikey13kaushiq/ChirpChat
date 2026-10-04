-- One database per service: each service owns its schema and migrates it with Flyway.
create database chirpchat_auth;
create database chirpchat_chat;
