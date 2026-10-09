# Cloud EPUB

A personal reading library with cloud storage and an Android reader.

## Language

**Cloud library**:
A user's private collection of books stored through the application and available across their devices.

**Cloud session**:
A device's authorized access to a user's cloud account. Revoking a Cloud session ends that cloud access while downloaded books may remain readable locally.

**Collection**:
A named grouping of books created by the user within their cloud library.

**Author entry**:
A named author grouping within a user's library. A user can reuse an existing entry or deliberately create a separate one, even with a similar name.

**Book details**:
The descriptive information associated with a book, including its title, authors, and release date.

**Release date**:
The publication date associated with a book, which may be known as a full date, a year only, or remain unknown.

**Library correction**:
A user-authored change to a book's author associations or release date in the cloud library, separate from the original imported publication.

**Book tile**:
A library item that presents a book's cover and title, with an options action for its details and other book actions.

**Shelf view**:
A library view arranged as a grid of tiles representing books, Collections, or Author entries.

**Cover fan**:
An overlapping arrangement of book covers representing the books in a Collection or Author entry.

**Cloud provider**:
An external file service from which a user can import books, initially Google Drive.

**Import**:
Adding a copy of a book from a device or cloud provider to the cloud library. Later changes to the source do not change the imported copy.

**Regular bookmark**:
A bookmark for the first visible content position at the top of the reading screen, preserved when font or screen changes alter the layout.

**Precise bookmark**:
A bookmark targeting a particular occurrence of a word in an imported book copy, preserved across changes to font size and screen layout.
_Avoid_: Advanced bookmark, specific bookmark

**Define**:
A reader action that displays a short definition of the selected word in a popup inside the application.

**Reading progress**:
The position in a book from which a user can continue reading.

**Recovery code**:
A secret kept by the user for restoring account access and unlocking their encrypted cloud library when the password is forgotten.

**Recovery package**:
The saved account identifier and Recovery code together, sufficient to locate the account and recover access without remembering its username.

**Image viewer button**:
A button at a meaningful image's position in the reading flow that opens the image in a popup above the reading text.
_Avoid_: Image button mode

**Qualifying activity**:
Successful sign-in or recovery, authenticated foreground app use, or synchronization of actual library changes that reaches the service and refreshes the cloud account's inactivity period. Offline reading, failed authentication, token refreshes, and empty background polling are not qualifying activity.

**Account expiration**:
The suspension of a cloud account after its inactivity period, while recovery remains possible during the grace period.

**Grace period**:
The interval after account expiration in which successful authentication can restore the cloud account before permanent deletion.
