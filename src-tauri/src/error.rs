use serde::{Serialize, Serializer};

/// Fehler, der als lesbarer Text ans Frontend geht.
#[derive(Debug)]
pub struct Error(String);

impl Error {
    pub fn msg(message: impl Into<String>) -> Self {
        Self(message.into())
    }

    pub fn message(&self) -> &str {
        &self.0
    }
}

// Absichtlich kein Display/std::error::Error, sonst kollidiert der Blanket-Impl
impl<E: std::error::Error> From<E> for Error {
    fn from(error: E) -> Self {
        Self(error.to_string())
    }
}

impl Serialize for Error {
    fn serialize<S: Serializer>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error> {
        serializer.serialize_str(&self.0)
    }
}

pub type Result<T> = std::result::Result<T, Error>;
