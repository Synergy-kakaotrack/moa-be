INSERT INTO users (
    user_id,
    user_email,
    user_name,
    profile_url,
    created_at
) VALUES (
             1,
             'lion01@gmail.com',
             'Lion',
             'https://example.com/profiles/lion.png',
             NOW()
         )
ON CONFLICT (user_id) DO NOTHING;

