def echo(text):
    return text[::-1]

# 示例测试
if __name__ == "__main__":
    test_input = "hello"
    result = echo(test_input)
    print(f"echo('{test_input}') = '{result}'")
    # 验证正确性
    assert result == "olleh", f"Expected 'olleh', got '{result}'"
    print("✅ PASS: echo works correctly.")
