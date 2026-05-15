-- V1__initial_schema.sql

CREATE TABLE IF NOT EXISTS public.file_metadata (
    id character varying(255) NOT NULL,
    last_modified timestamp(6) with time zone,
    owner_id character varying(255),
    relative_path character varying(255),
    sha256hash character varying(255),
    size bigint NOT NULL,
    status character varying(255),
    version_vector_json text,
    CONSTRAINT file_metadata_pkey PRIMARY KEY (id),
    CONSTRAINT file_metadata_status_check CHECK (status IN ('SYNCED', 'MODIFIED', 'REMOTE_NEW', 'CONFLICT'))
);

CREATE TABLE IF NOT EXISTS public.file_shared_with (
    file_id character varying(255) NOT NULL,
    user_id character varying(255)
);

ALTER TABLE ONLY public.file_shared_with
    ADD CONSTRAINT fki1fuwjcetd1q9q7h1rommtdvx FOREIGN KEY (file_id) REFERENCES public.file_metadata(id);

CREATE TABLE IF NOT EXISTS public.sync_tasks (
    task_id character varying(255) NOT NULL,
    actions_json text,
    created_at timestamp(6) without time zone,
    error_message character varying(255),
    owner_id character varying(255),
    status character varying(255),
    updated_at timestamp(6) without time zone,
    CONSTRAINT sync_tasks_pkey PRIMARY KEY (task_id)
);

CREATE SEQUENCE IF NOT EXISTS public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS public.users (
    id bigint NOT NULL DEFAULT nextval('public.users_id_seq'),
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    reset_token character varying(255),
    token_expiry timestamp(6) without time zone,
    username character varying(255) NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email),
    CONSTRAINT uk_r43af9ap4edm43mmtq01oddj6 UNIQUE (username)
);

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;