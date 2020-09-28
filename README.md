# Escalar Alcoià i Comtat #

This is the official Escalar Alcoià i Comtat's Android App repository, here's the full source code of the app.

All the credentials are hidden, and embed in the build Pipeline.

## Play Store ##
The app is available at the Play Store. You can access it [here](https://play.google.com/store/apps/details?id=com.arnyminerz.escalaralcoiaicomtat).

## Issues ##
Whatever problem that is detected by the community can be reported directly to [Jira](https://escalaralcoiaicomtat.atlassian.net/jira/software/projects/EAIC/boards).

## Build ##
The builds are automatically generated by the Pipeline, so anyone can access the private credentials.

Generated APK and other download files can be accessed directly from the [Bitbucket Downloads Page](https://bitbucket.org/escalar-alcoia-i-comtat/android-app/downloads/).

## Donations ##
We are really pleased to receive donations from the people that want to. It's not mandatory, you can even disable the in-app ads, but since we have put a lot of hours in the app, we really love that people love our work.

We have prepared a page in Patreon so you can subscribe with a monthly membership with some off-app benefits. You can access it from [Patreon](https://www.patreon.com/escalaralcoiaicomtat).

# Documentation
## Automated Notifications
The notifications received from the Firebase Cloud Messaging service (`FirebaseMessagingService.kt`) get processed based on the title of the received notification. Note that for the app to the detect the title, it must be prefixed with `*`. If it doesn't match any of the specified titles, the notification shows as received.

### `new_friend_request`
When the user receives a friend request.<br/>
**Key:**: `NOTIFICATION_TYPE_NEW_FRIEND_REQUEST`<br/>
**Channel**: `FRIEND_REQUEST_CHANNEL_ID` (<`FriendRequest`>)<br/>
**Data**:
- `from_uid`: The uid of the user that requested the notification.

### `friend_request_accepted`
When a friend request the user's sent gets accepted.<br/>
**Key:**: `NOTIFICATION_TYPE_FRIEND_REQUEST_ACCEPTED`<br/>
**Channel**: `FRIEND_REQUEST_ACCEPTED_CHANNEL_ID` (<`FriendRequestAccepted`>)<br/>
**Data**:
- `user_uid`: The uid of the user that accepted the request.

### `friend_removed`
When a user removes someone from its friend list, the removed user gets informed.<br/>
**Key:**: `NOTIFICATION_TYPE_FRIEND_REMOVED`<br/>
**Channel**: `FRIEND_REMOVED_CHANNEL_ID` (<`FriendRemovedAccepted`>)<br/>
**Data**:
- `user_uid`: The uid of the user that removed the receiver as friend.

### `new_update_beta`
When a new version in the beta channel is available.<br/>
**Key:**: `NOTIFICATION_TYPE_NEW_UPDATE_BETA`<br/>
**Channel**: `BETA_UPDATE_CHANNEL_ID` (<`BetaUpdate`>)<br/>
**Data**:
- `url`: The url of the new APK.

### `user_liked`
When a user likes a 'post' the user has made. Such as a completed path.<br/>
**Key:**: `NOTIFICATION_TYPE_USER_LIKED`<br/>
**Channel**: `USER_INTERACTED_CHANNEL_ID` (<`BetaUpdate`>)<br/>
**Data**:
- `user_uid`: The uid of the user that liked the post.
- `path_name`: The display name of the path that was liked.