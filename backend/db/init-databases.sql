-- Runs once, the first time the postgres container initializes an empty data
-- directory (the official image's documented behaviour for anything mounted
-- into /docker-entrypoint-initdb.d/). Every backend service already has its
-- own database name hardcoded into application.properties - tunindex-api,
-- tunindex-collector, tunindex-ads, tunindex-mailing, tunindex-sms,
-- tunindex-recaptcha - this just makes sure all six exist before any service
-- tries to connect. POSTGRES_DB in docker-compose.yml creates the first one
-- (tunindex-api); the other five are created here.
--
-- A .sql file rather than a .sh script on purpose: it is fed to psql
-- regardless of the Unix executable bit, which a Windows host's bind mount
-- does not reliably preserve.
CREATE DATABASE "tunindex-collector";
CREATE DATABASE "tunindex-ads";
CREATE DATABASE "tunindex-mailing";
CREATE DATABASE "tunindex-sms";
CREATE DATABASE "tunindex-recaptcha";
