-- User ids widen to bigint, as request ids did in V1.008, so the user table cannot run out of
-- them. Unlike V1.008 the values are kept: tokens and the web app's sessions carry a user's id,
-- and application.user_id points at it.

alter table application alter column user_id type bigint;

alter table marketplace_user alter column id type bigint;

alter sequence marketplace_user_id_seq as bigint;
