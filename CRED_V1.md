Portfolio - Secure Credential Vault V1
Overview

The Portfolio application currently stores sensitive credentials externally (e.g., Excel sheets). The goal of this module is to provide a secure built-in Credential Vault that stores application credentials in encrypted format within SQLite.

The solution must follow security principles similar to KeePass and Bitwarden while remaining self-contained and independent of Oracle Cloud Vault.

Primary Objective

Replace Excel-based password storage with a secure vault that:

Stores credentials in encrypted format.
Never stores passwords in plaintext.
Never stores Master Password.
Never stores encryption keys in plaintext.
Requires Master Password verification before revealing passwords.
Keeps passwords hidden by default.
Uses industry-standard encryption.
Technology Stack
Java 21
Spring Boot
SQLite Database
JPA / Hibernate
AES-256-GCM Encryption
Argon2id Key Derivation
Oracle Cloud Hosting
Self-managed vault configuration
No dependency on Oracle Cloud Vault
Security Architecture

The vault uses a layered encryption model.

Encryption Flow
Plain Text
Master Password
↓
Argon2id + Salt
↓
Key Encryption Key (KEK)
↓
Decrypt Encrypted DEK
↓
Data Encryption Key (DEK)
↓
AES-256-GCM
↓
Credential Password Encryption
Key Concepts
Master Password

The user creates a Master Password during initial setup.

Example:

Plain Text
MyPortfolio@2026

Rules:

Never store in database.
Never log.
Never cache permanently.
Used only for authentication and key derivation.
Key Encryption Key (KEK)

Generated dynamically using:

Plain Text
Argon2id

Flow:

Plain Text
Master Password
↓
Argon2id + Salt
↓
KEK

Purpose:

Plain Text
Protects DEK
Data Encryption Key (DEK)

Randomly generated:

Plain Text
256-bit AES Key

Purpose:

Plain Text
Actual encryption/decryption of credentials
DEK Protection

DEK must never be stored in plaintext.

Flow:

Plain Text
DEK
↓
Encrypted by KEK
↓
Encrypted DEK

Store only encrypted version in database.

Database Design
credentials Table
SQL
CREATE TABLE credentials (
id INTEGER PRIMARY KEY AUTOINCREMENT,
client_pan TEXT NOT NULL,
app_name TEXT NOT NULL,
username TEXT NOT NULL,
encrypted_password TEXT NOT NULL,
password_iv TEXT NOT NULL,
website TEXT,
notes TEXT,
created_at DATETIME,
updated_at DATETIME
);
Column Usage
id = Unique Identifier
client_pan = PAN Number
app_name = Application Name
username = Username/Login ID
encrypted_password = Encrypted password value
password_iv = AES-GCM IV
website = Login URL
notes = Additional remarks
created_at = Creation timestamp
updated_at = Update timestamp
vault_config Table

Purpose:

Store metadata required to unlock the vault.

SQL
CREATE TABLE vault_config (
id INTEGER PRIMARY KEY,
encrypted_dek TEXT NOT NULL,
dek_iv TEXT NOT NULL,
salt TEXT NOT NULL,
created_at DATETIME
);
Column Usage
encrypted_dek = Encrypted DEK
dek_iv = IV used during DEK encryption
salt = Argon2 salt
created_at = Creation timestamp

Notes:

Only one row should exist.
This table is not Oracle Cloud Vault.
This is an internally managed vault configuration.
credential_audit Table
SQL
CREATE TABLE credential_audit (
id INTEGER PRIMARY KEY AUTOINCREMENT,
action_type TEXT,
credential_id INTEGER,
performed_at DATETIME,
performed_by TEXT
);

Supported Audit Actions:

Plain Text
CREATE
UPDATE
DELETE
VIEW_PASSWORD
COPY_PASSWORD

Never store:

Plain Text
Actual Password
Master Password
Decrypted Values
Encryption Keys
Initial Vault Setup Flow

When no vault exists:

Screen
Plain Text
---------------------------------
Create Vault
---------------------------------
Master Password
Confirm Password
 
[Create Vault]
Processing
Plain Text
Create Salt
↓
Generate DEK
↓
Generate KEK from Master Password
↓
Encrypt DEK using KEK
↓
Store encrypted DEK
↓
Store Salt
↓
Vault Initialized
Vault Unlock Flow

Every new session:

Screen
Plain Text
---------------------------------
Unlock Vault
---------------------------------
Master Password
 
[Unlock]
Processing
Plain Text
Read Salt
↓
Generate KEK
↓
Decrypt DEK
↓
Store DEK in memory
↓
Vault Unlocked
Credential Listing Screen
Grid Layout
Plain Text
------------------------------------------------------------------------------------------------
PAN App Name Username Password Website
------------------------------------------------------------------------------------------------
ABCDE1234F Gmail raj@gmail.com ******** gmail.com
ABCDE1234F Zerodha raj123 ******** zerodha.com
ABCDE1234F Groww rajuser ******** groww.in
------------------------------------------------------------------------------------------------

Rules:

Passwords always masked.
Passwords never loaded in plaintext.
No automatic decryption.
No bulk reveal functionality.
Row Actions

Each credential supports:

Plain Text
View
Edit
Delete
Reveal Password
Copy Password
Add Credential Screen
Fields
Plain Text
Client PAN *
Application Name *
Username *
Password *
Website
Notes

Buttons:

Plain Text
Save
Cancel
Save Processing
Plain Text
Password
↓
Encrypt Using DEK
↓
Generate IV
↓
Store Cipher Text
↓
Save Credential
Edit Credential Screen
Fields
Plain Text
Client PAN
Application Name
Username
Password (Masked)
Website
Notes

Rules:

Password starts masked.
Password not automatically revealed.
User must explicitly authenticate to view.
Reveal Password Behaviour

Passwords must remain hidden by default.

Displayed:

Plain Text
************

When user clicks:

Plain Text
Reveal Password

Open dialog:

Plain Text
---------------------------------
Verify Master Password
---------------------------------
Master Password
 
[Verify]
[Cancel]
 
---------------------------------

Flow:

Plain Text
Verify Password
↓
Generate KEK
↓
Decrypt DEK
↓
Decrypt Credential Password
↓
Display Password

Display example:

Plain Text
MySecretPassword123

Auto-hide after:

Plain Text
30 seconds

or

Plain Text
Dialog Close
Copy Password Behaviour

When user clicks:

Plain Text
Copy Password

Open verification dialog:

Plain Text
Master Password

Flow:

Plain Text
Verify Master Password
↓
Decrypt Password
↓
Copy To Clipboard

Notification:

Plain Text
Password copied successfully.

Optional:

Plain Text
Clear clipboard after 30 seconds.
Delete Credential Behaviour

Before deletion:

Plain Text
Are you sure you want to delete this credential?
 
[Delete]
[Cancel]

Processing:

Plain Text
Delete Credential
↓
Write Audit Entry
Session Management
DEK Storage

DEK may exist only:

Plain Text
In Server Memory

Never stored in:

Plain Text
Database
Logs
Application Properties
Files
Browser Local Storage
Cookies
Session Storage
Session Auto Lock

If inactive:

Plain Text
10 minutes

System must:

Plain Text
Remove DEK from memory
Lock Vault
Require Master Password again
Security Rules
Never Store
Plain Text
Plaintext Password
Master Password
Plaintext DEK
Plaintext KEK
Never Log
Plain Text
Passwords
Master Password
Encryption Keys
Decrypted Data
Encryption Standard
Plain Text
AES/GCM/NoPadding

Key Size:

Plain Text
256-bit
Key Derivation
Plain Text
Argon2id

Purpose:

Plain Text
Master Password -> KEK
IV Rules
Plain Text
Generate random IV every encryption operation.
Never reuse IV.
Store IV alongside encrypted data.
User Permissions (V1)

Single User System.

Authenticated user may:

Plain Text
Unlock Vault
Add Credential
Update Credential
Delete Credential
Reveal Password
Copy Password
Future Enhancements (Not In V1)
Multi-user support
Role-based access control
Credential categories
Password expiry tracking
Secure file attachments
TOTP/2FA secret storage
Password generator
Password strength indicator
Vault export/import
Emergency recovery process
Final Security Goal

The system should remain secure if:

SQLite database is stolen.
Database backup is leaked.
Source code is leaked.
Read-only server access is obtained.
Credential database is copied.

An attacker must still know the user's Master Password in order to derive the KEK, decrypt the DEK, and ultimately decrypt any stored credentials.

The Master Password is the root of trust for the entire Portfolio Credential Vault.